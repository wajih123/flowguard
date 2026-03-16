"""
Auto-retrain scheduler for the FlowGuard LSTM model.

Runs as a background thread (or as a standalone scheduled job) and:
1. Collects recent model evaluation metrics.
2. Compares current MAE against the baseline (last 30d rolling average).
3. If degradation > DEGRADATION_THRESHOLD, triggers a full retrain.
4. After retraining, validates improvement before promoting to production.

Usage (standalone):
    python -m app.training.auto_retrain

Usage (embedded in main.py):
    from app.training.auto_retrain import AutoRetrainScheduler
    scheduler = AutoRetrainScheduler()
    scheduler.start()
"""
from __future__ import annotations

import logging
import threading
import time
from datetime import datetime, timezone
from typing import Optional

from app.db import (
    get_active_model_version,
    get_baseline_mae,
    get_recent_mae,
    promote_model_version,
)
from app.training.trainer import Trainer

log = logging.getLogger(__name__)

# ── Configuration ──────────────────────────────────────────────────────────────
DEGRADATION_THRESHOLD = 0.15   # 15% MAE degradation triggers retrain
CHECK_INTERVAL_HOURS  = 6      # Check every 6 hours
MIN_SAMPLES_FOR_CHECK = 50     # Min prediction samples before evaluating drift
RETRAIN_COOLDOWN_HOURS = 24    # Don't retrain more than once per 24h


class AutoRetrainScheduler:
    """Background scheduler that monitors model performance and retrains automatically."""

    def __init__(self) -> None:
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._last_retrain: Optional[datetime] = None

    def start(self) -> None:
        """Start the background scheduling thread."""
        if self._thread and self._thread.is_alive():
            log.warning("AutoRetrainScheduler already running")
            return

        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run_loop,
            name="auto-retrain-scheduler",
            daemon=True,
        )
        self._thread.start()
        log.info("AutoRetrainScheduler started (check interval: %dh)", CHECK_INTERVAL_HOURS)

    def stop(self) -> None:
        """Signal the scheduler to stop."""
        self._stop_event.set()
        if self._thread:
            self._thread.join(timeout=10)
        log.info("AutoRetrainScheduler stopped")

    def _run_loop(self) -> None:
        """Main scheduling loop."""
        while not self._stop_event.is_set():
            try:
                self._check_and_retrain()
            except Exception as exc:
                log.error("Error in auto-retrain loop: %s", exc, exc_info=True)

            # Sleep in small increments to allow clean shutdown
            interval_secs = CHECK_INTERVAL_HOURS * 3600
            for _ in range(interval_secs // 30):
                if self._stop_event.is_set():
                    return
                time.sleep(30)

    def _check_and_retrain(self) -> None:
        """Evaluate model drift and trigger retrain if needed."""
        log.info("Checking model performance drift...")

        active_version = get_active_model_version()
        if active_version is None:
            log.info("No active model version found — skipping drift check")
            return

        baseline_mae = get_baseline_mae()
        if baseline_mae is None:
            log.info("No baseline MAE available")
            return

        # Get MAE from the last 7 days of live predictions
        recent_mae = get_recent_mae(days=7, min_samples=MIN_SAMPLES_FOR_CHECK)
        if recent_mae is None:
            log.info("Not enough recent predictions to evaluate drift (min=%d)", MIN_SAMPLES_FOR_CHECK)
            return

        degradation = (recent_mae - baseline_mae) / baseline_mae
        log.info(
            "Model drift check: baseline_mae=%.4f recent_mae=%.4f degradation=%.1f%%",
            baseline_mae, recent_mae, degradation * 100,
        )

        if degradation <= DEGRADATION_THRESHOLD:
            log.info("Model performance OK (degradation %.1f%% ≤ %.0f%% threshold)",
                     degradation * 100, DEGRADATION_THRESHOLD * 100)
            return

        # Check cooldown
        now = datetime.now(tz=timezone.utc)
        if self._last_retrain is not None:
            hours_since = (now - self._last_retrain).total_seconds() / 3600
            if hours_since < RETRAIN_COOLDOWN_HOURS:
                log.warning(
                    "Drift detected but retrain cooldown active (%.1fh/%dh remaining). Skipping.",
                    hours_since, RETRAIN_COOLDOWN_HOURS,
                )
                return

        log.warning(
            "⚠️  Model degradation %.1f%% exceeds threshold %.0f%% — triggering retrain",
            degradation * 100, DEGRADATION_THRESHOLD * 100,
        )
        self._trigger_retrain(baseline_mae)

    def _trigger_retrain(self, baseline_mae: float) -> None:
        """Run a full retrain cycle and promote if improved."""
        now = datetime.now(tz=timezone.utc)
        self._last_retrain = now

        try:
            trainer = Trainer()
            result = trainer.train()

            if result is None:
                log.error("Retrain returned no result — aborting promotion")
                return

            new_mae = result.evaluation_report.mae
            improvement = (baseline_mae - new_mae) / baseline_mae

            if new_mae < baseline_mae:
                log.info(
                    "✅ Retrained model improved by %.1f%% (old=%.4f new=%.4f) — promoting to production",
                    improvement * 100, baseline_mae, new_mae,
                )
                promote_model_version(result.version)
            else:
                log.warning(
                    "❌ Retrained model did not improve (old=%.4f new=%.4f) — keeping current version",
                    baseline_mae, new_mae,
                )

        except Exception as exc:
            log.error("Retrain failed: %s", exc, exc_info=True)


# ── Standalone entry point ─────────────────────────────────────────────────────

def run_once() -> None:
    """Run a single drift check + retrain cycle (for cron jobs or CI triggers)."""

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

    log.info("Running single auto-retrain check...")
    scheduler = AutoRetrainScheduler()
    scheduler._check_and_retrain()
    log.info("Done.")


if __name__ == "__main__":
    run_once()
