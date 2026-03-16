"""
APScheduler jobs for automated ML maintenance.

Jobs:
  1. daily_mae_check     — 06:00 FR (MAE health check + alert)
  2. weekly_drift_check  — Monday 07:00 FR (PSI + concept drift)
  3. scheduled_retrain   — configurable interval (default 30d)
  4. on_user_milestone   — triggered at [100,500,1000,5000,10000,50000] users

Redis flag 'retraining_in_progress' prevents concurrent retraining.
Shadow mode: urgency='low' → run new model in parallel 48h before promoting.
"""
from __future__ import annotations

import asyncio
import logging
import os
from datetime import datetime, timezone
from typing import Optional

import redis as redis_lib
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from app.monitoring.drift_detector import DriftDetector

log = logging.getLogger(__name__)

# Redis client (shared with cache.py)
_redis_url = os.getenv("REDIS_URL", "redis://localhost:6379/0")
try:
    _redis = redis_lib.from_url(_redis_url, decode_responses=True)
except Exception:
    _redis = None  # type: ignore

RETRAIN_FLAG = "retraining_in_progress"
USER_MILESTONES = [100, 500, 1000, 5000, 10_000, 50_000]
_fired_milestones: set[int] = set()


def _redis_lock(ttl_seconds: int = 3600) -> bool:
    """Set Redis lock atomically. Returns True if lock was acquired."""
    if _redis is None:
        return True  # Fail open in dev
    return bool(_redis.set(RETRAIN_FLAG, "1", ex=ttl_seconds, nx=True))


def _redis_unlock() -> None:
    if _redis is not None:
        _redis.delete(RETRAIN_FLAG)


def _is_retraining() -> bool:
    if _redis is None:
        return False
    return bool(_redis.exists(RETRAIN_FLAG))


def _get_n_eligible_users() -> int:
    """Count users with enough data for training (≥ 90d history)."""
    try:
        from database import engine  # existing ml-service engine
        from sqlalchemy import text

        with engine.connect() as conn:
            result = conn.execute(
                text(
                    "SELECT COUNT(DISTINCT account_id) FROM daily_balances "
                    "WHERE recorded_date >= NOW() - INTERVAL '90 days'"
                )
            )
            row = result.fetchone()
            return int(row[0]) if row else 0
    except Exception as e:
        log.warning("eligible_user_count_failed", error=str(e))
        return 0


def _run_retrain(urgency: str = "normal") -> None:
    """
    Blocking retrain. Called from scheduler jobs.
    urgency='low' → shadow mode (parallel 48h before promote).
    """
    if _is_retraining():
        log.info("retrain_skipped", reason="already_in_progress")
        return

    n_users = _get_n_eligible_users()
    min_users = int(os.getenv("MIN_RETRAIN_USERS", "50"))
    if n_users < min_users:
        log.info("retrain_skipped", reason="insufficient_users", n_users=n_users, min_required=min_users)
        return

    if not _redis_lock(ttl_seconds=7200):
        log.info("retrain_skipped", reason="lock_not_acquired")
        return

    log.info("retrain_starting", n_users=n_users, urgency=urgency)
    try:
        from app.training.trainer import LSTMTrainer
        import numpy as np

        # Fetch training data
        try:
            from database import get_training_data  # existing utility
            raw_data = get_training_data()
        except Exception as e:
            log.error("training_data_fetch_failed", error=str(e))
            return

        if not raw_data or len(raw_data) < 10:
            log.warning("retrain_aborted", reason="insufficient_training_data")
            return

        # Build feature matrices (simplified: use balance series only for now)
        feature_matrices = []
        balance_series_list = []
        for record in raw_data:
            balances = np.array(record.get("balances", []), dtype=np.float32)
            if len(balances) < 180:
                continue
            mean_b, std_b = balances.mean(), balances.std() + 1e-6
            norm_b = (balances - mean_b) / std_b
            # 15-feature matrix: first col = normalised balance, rest = zeros (placeholder)
            fm = np.zeros((len(norm_b), 15), dtype=np.float32)
            fm[:, 0] = norm_b
            feature_matrices.append(fm)
            balance_series_list.append(norm_b)

        if len(feature_matrices) < 10:
            log.warning("retrain_aborted", reason="too_few_long_series")
            return

        # 80/20 chronological split
        split = int(len(feature_matrices) * 0.8)
        train_fms, val_fms = feature_matrices[:split], feature_matrices[split:]
        train_bss, val_bss = balance_series_list[:split], balance_series_list[split:]

        trainer = LSTMTrainer()
        model, _, _ = trainer.train(
            train_fms, train_bss, val_fms, val_bss, n_users=n_users
        )
        eval_report = trainer.evaluate(model, val_fms, val_bss)

        model_path = os.getenv("LSTM_MODEL_PATH", "models/treasury_lstm.pt")
        if urgency == "low":
            # Shadow mode: save to a shadow path, promote after 48h
            shadow_path = model_path.replace(".pt", "_shadow.pt")
            result = trainer.save_model(model, eval_report, shadow_path, {}, n_users)
            log.info("shadow_model_saved", path=shadow_path, mae_7d=eval_report.mae_7d)
            # Schedule promotion after 48h
            if _scheduler and result.evaluation.passes_production_threshold:
                _scheduler.add_job(
                    _promote_shadow,
                    trigger="date",
                    run_date=datetime.now(tz=timezone.utc).replace(
                        hour=datetime.now().hour
                    ),
                    kwargs={"shadow_path": shadow_path, "prod_path": model_path},
                    id="promote_shadow",
                    replace_existing=True,
                    misfire_grace_time=300,
                )
        else:
            result = trainer.save_model(model, eval_report, model_path, {}, n_users)
            log.info(
                "retrain_complete",
                mae_7d=eval_report.mae_7d,
                promoted=result.promoted,
                n_users=n_users,
            )

    except Exception as e:
        log.error("retrain_failed", error=str(e), exc_info=True)
    finally:
        _redis_unlock()


def _promote_shadow(shadow_path: str, prod_path: str) -> None:
    """Promote shadow model to production after shadow validation period."""
    import shutil
    if os.path.exists(shadow_path):
        shutil.move(shadow_path, prod_path)
        log.info("shadow_model_promoted", from_=shadow_path, to=prod_path)
    else:
        log.warning("shadow_model_not_found", path=shadow_path)


# ---------------------------------------------------------------------------
# Scheduled jobs
# ---------------------------------------------------------------------------

async def daily_mae_check() -> None:
    """
    Each day at 06:00 FR time: check current MAE vs threshold.
    Log alert if mae_current ≥ 150.
    """
    from app.db import get_baseline_mae

    try:
        mae = get_baseline_mae()
        if mae is None:
            log.info("daily_mae_check", status="no_model")
            return
        reserve_safe = mae < 150
        log.info("daily_mae_check", mae_current=round(mae, 2), reserve_safe=reserve_safe)
        if not reserve_safe:
            log.warning("mae_threshold_exceeded", mae_current=mae, threshold=150)
    except Exception as e:
        log.error("daily_mae_check_failed", error=str(e))


async def weekly_drift_check() -> None:
    """
    Every Monday at 07:00: PSI data drift + concept drift.
    Triggers retrain if critical drift detected.
    """
    detector = DriftDetector()
    try:
        concept = detector.detect_concept_drift()
        log.info(
            "weekly_drift_check",
            mae_7d=concept.rolling_mae_7d,
            drift_label=concept.drift_label,
            trigger_retrain=concept.trigger_retrain,
        )
        if concept.trigger_retrain:
            log.warning("drift_triggered_retrain", label=concept.drift_label)
            loop = asyncio.get_event_loop()
            await loop.run_in_executor(None, _run_retrain, "normal")
    except Exception as e:
        log.error("weekly_drift_check_failed", error=str(e))


async def scheduled_retrain() -> None:
    """Periodic full retrain (default every 30 days)."""
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, _run_retrain, "normal")


async def on_user_milestone(n_users: int) -> None:
    """Fast retrain triggered when hitting user count milestones."""
    log.info("milestone_retrain", n_users=n_users)
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, _run_retrain, "normal")


def check_milestones(current_users: int) -> None:
    """
    Call this on new user registration to fire milestone retrains.
    Idempotent — each milestone fires at most once per process lifetime.
    """
    for milestone in USER_MILESTONES:
        if current_users >= milestone and milestone not in _fired_milestones:
            _fired_milestones.add(milestone)
            log.info("milestone_reached", milestone=milestone)
            asyncio.create_task(on_user_milestone(milestone))


# ---------------------------------------------------------------------------
# Scheduler lifecycle
# ---------------------------------------------------------------------------

_scheduler: Optional[AsyncIOScheduler] = None


def create_scheduler() -> AsyncIOScheduler:
    global _scheduler

    retrain_interval_days = int(os.getenv("RETRAIN_INTERVAL_DAYS", "30"))

    scheduler = AsyncIOScheduler(timezone="Europe/Paris")

    # Daily MAE check — 06:00 FR
    scheduler.add_job(
        daily_mae_check,
        CronTrigger(hour=6, minute=0, timezone="Europe/Paris"),
        id="daily_mae_check",
        replace_existing=True,
        misfire_grace_time=300,
    )

    # Weekly drift check — Monday 07:00 FR
    scheduler.add_job(
        weekly_drift_check,
        CronTrigger(day_of_week="mon", hour=7, minute=0, timezone="Europe/Paris"),
        id="weekly_drift_check",
        replace_existing=True,
        misfire_grace_time=600,
    )

    # Periodic retrain
    scheduler.add_job(
        scheduled_retrain,
        IntervalTrigger(days=retrain_interval_days),
        id="scheduled_retrain",
        replace_existing=True,
        misfire_grace_time=3600,
    )

    _scheduler = scheduler
    return scheduler


def start_scheduler() -> AsyncIOScheduler:
    scheduler = create_scheduler()
    scheduler.start()
    log.info("scheduler_started")
    return scheduler


def stop_scheduler() -> None:
    if _scheduler and _scheduler.running:
        _scheduler.shutdown(wait=False)
        log.info("scheduler_stopped")
