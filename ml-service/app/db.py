"""
FlowGuard ML Service — Database utilities.
ML-specific tables on top of the existing PostgreSQL schema.
"""
from __future__ import annotations

import json
import os
from datetime import date, datetime
from typing import Optional

import structlog
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

log = structlog.get_logger()

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://flowguard:flowguard_secret@localhost:5432/flowguard",
)

_engine = None


def get_engine():
    global _engine
    if _engine is None:
        _engine = create_engine(
            DATABASE_URL, pool_size=5, max_overflow=10, pool_pre_ping=True
        )
    return _engine


def get_session():
    return sessionmaker(bind=get_engine())()


def ensure_ml_tables() -> None:
    """Create ML-specific tables if they don't exist (idempotent)."""
    ddl_statements = [
        """
        CREATE TABLE IF NOT EXISTS model_versions (
            id                BIGSERIAL PRIMARY KEY,
            version           VARCHAR(20)  NOT NULL,
            created_at        TIMESTAMPTZ  DEFAULT NOW(),
            mae_7d            FLOAT        NOT NULL,
            mae_30d           FLOAT        NOT NULL,
            mae_90d           FLOAT        NOT NULL,
            deficit_recall    FLOAT        NOT NULL,
            deficit_precision FLOAT        NOT NULL,
            n_users_trained   INT          NOT NULL,
            model_path        TEXT         NOT NULL,
            config            JSONB        DEFAULT '{}',
            status            VARCHAR(20)  DEFAULT 'CANDIDATE'
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS user_scalers (
            user_id    VARCHAR(36)  PRIMARY KEY,
            mean       FLOAT        NOT NULL,
            std        FLOAT        NOT NULL,
            updated_at TIMESTAMPTZ  DEFAULT NOW()
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS prediction_actuals (
            id                BIGSERIAL    PRIMARY KEY,
            prediction_id     VARCHAR(64)  NOT NULL,
            user_id           VARCHAR(36)  NOT NULL,
            prediction_date   TIMESTAMPTZ  NOT NULL,
            target_date       DATE         NOT NULL,
            predicted_balance FLOAT        NOT NULL,
            actual_balance    FLOAT,
            absolute_error    FLOAT,
            percentage_error  FLOAT,
            created_at        TIMESTAMPTZ  DEFAULT NOW()
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS prediction_quality_log (
            id               BIGSERIAL  PRIMARY KEY,
            log_date         DATE       NOT NULL,
            mae_7d           FLOAT,
            mae_30d          FLOAT,
            drift_ratio_7d   FLOAT,
            drift_ratio_30d  FLOAT,
            alert_triggered  BOOLEAN    DEFAULT FALSE,
            created_at       TIMESTAMPTZ DEFAULT NOW()
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS ml_retrain_log (
            id            BIGSERIAL    PRIMARY KEY,
            started_at    TIMESTAMPTZ  NOT NULL,
            completed_at  TIMESTAMPTZ,
            reason        VARCHAR(100),
            urgency       VARCHAR(20),
            status        VARCHAR(20),
            model_version VARCHAR(20),
            n_users       INT,
            final_mae     FLOAT,
            error         TEXT,
            created_at    TIMESTAMPTZ  DEFAULT NOW()
        )
        """,
    ]
    try:
        with get_engine().connect() as conn:
            for ddl in ddl_statements:
                conn.execute(text(ddl))
            conn.commit()
        log.info("ml_tables_ensured")
    except Exception as e:
        log.error("ml_tables_creation_failed", error=str(e))
        raise


# ── Model version CRUD ─────────────────────────────────────────────────────────

def save_model_version(
    version: str,
    mae_7d: float,
    mae_30d: float,
    mae_90d: float,
    deficit_recall: float,
    deficit_precision: float,
    n_users_trained: int,
    model_path: str,
    config: dict,
    status: str = "CANDIDATE",
) -> int:
    with get_session() as session:
        result = session.execute(
            text("""
                INSERT INTO model_versions
                    (version, mae_7d, mae_30d, mae_90d,
                     deficit_recall, deficit_precision,
                     n_users_trained, model_path, config, status)
                VALUES
                    (:version, :mae_7d, :mae_30d, :mae_90d,
                     :deficit_recall, :deficit_precision,
                     :n_users_trained, :model_path, :config::jsonb, :status)
                RETURNING id
            """),
            {
                "version": version,
                "mae_7d": mae_7d,
                "mae_30d": mae_30d,
                "mae_90d": mae_90d,
                "deficit_recall": deficit_recall,
                "deficit_precision": deficit_precision,
                "n_users_trained": n_users_trained,
                "model_path": model_path,
                "config": json.dumps(config),
                "status": status,
            },
        )
        row_id = result.fetchone()[0]
        session.commit()
        return row_id


def get_active_model_version() -> Optional[dict]:
    with get_session() as session:
        result = session.execute(
            text("SELECT * FROM model_versions WHERE status='ACTIVE' ORDER BY created_at DESC LIMIT 1")
        )
        row = result.mappings().fetchone()
        return dict(row) if row else None


def promote_model_version(version: str) -> None:
    with get_session() as session:
        session.execute(
            text("UPDATE model_versions SET status='DEPRECATED' WHERE status='ACTIVE'")
        )
        session.execute(
            text("UPDATE model_versions SET status='ACTIVE' WHERE version=:v"),
            {"v": version},
        )
        session.commit()


# ── User scalers ───────────────────────────────────────────────────────────────

def save_user_scaler(user_id: str, mean: float, std: float) -> None:
    with get_session() as session:
        session.execute(
            text("""
                INSERT INTO user_scalers (user_id, mean, std)
                VALUES (:uid, :mean, :std)
                ON CONFLICT (user_id) DO UPDATE
                    SET mean=EXCLUDED.mean, std=EXCLUDED.std, updated_at=NOW()
            """),
            {"uid": user_id, "mean": mean, "std": std},
        )
        session.commit()


def load_user_scaler(user_id: str) -> Optional[tuple[float, float]]:
    with get_session() as session:
        result = session.execute(
            text("SELECT mean, std FROM user_scalers WHERE user_id=:uid"),
            {"uid": user_id},
        )
        row = result.fetchone()
        return (row[0], row[1]) if row else None


# ── Prediction actuals ─────────────────────────────────────────────────────────

def record_prediction_actual(
    prediction_id: str,
    user_id: str,
    prediction_date: datetime,
    target_date: date,
    predicted_balance: float,
    actual_balance: float,
) -> None:
    abs_err = abs(actual_balance - predicted_balance)
    pct_err = abs_err / max(abs(actual_balance), 1.0) * 100.0
    with get_session() as session:
        session.execute(
            text("""
                INSERT INTO prediction_actuals
                    (prediction_id, user_id, prediction_date, target_date,
                     predicted_balance, actual_balance, absolute_error, percentage_error)
                VALUES (:pid, :uid, :pdate, :tdate, :pred, :actual, :abs_err, :pct_err)
            """),
            {
                "pid": prediction_id,
                "uid": user_id,
                "pdate": prediction_date,
                "tdate": target_date,
                "pred": predicted_balance,
                "actual": actual_balance,
                "abs_err": abs_err,
                "pct_err": pct_err,
            },
        )
        session.commit()


def get_recent_actuals(days: int = 30) -> list[dict]:
    with get_session() as session:
        result = session.execute(
            text("""
                SELECT * FROM prediction_actuals
                WHERE prediction_date >= NOW() - INTERVAL ':days days'
                ORDER BY prediction_date DESC
            """),
            {"days": days},
        )
        return [dict(r) for r in result.mappings().fetchall()]


def get_baseline_mae() -> Optional[float]:
    """Return MAE from the currently active model version."""
    mv = get_active_model_version()
    return mv["mae_7d"] if mv else None


def get_recent_mae(days: int = 7, min_samples: int = 50) -> Optional[float]:
    """
    Compute mean absolute error from recent prediction_actuals.
    Returns None if there aren't enough samples to make a reliable estimate.
    """
    with get_session() as session:
        result = session.execute(
            text("""
                SELECT COUNT(*) as cnt, AVG(absolute_error) as avg_mae
                FROM prediction_actuals
                WHERE prediction_date >= NOW() - (:days || ' days')::interval
                  AND actual_balance IS NOT NULL
            """),
            {"days": days},
        )
        row = result.fetchone()
        if row and row[0] >= min_samples:
            return float(row[1]) if row[1] is not None else None
        return None


# ── Quality log ────────────────────────────────────────────────────────────────

def append_quality_log(
    log_date: date,
    mae_7d: float,
    mae_30d: float,
    drift_ratio_7d: float,
    drift_ratio_30d: float,
    alert_triggered: bool,
) -> None:
    with get_session() as session:
        session.execute(
            text("""
                INSERT INTO prediction_quality_log
                    (log_date, mae_7d, mae_30d, drift_ratio_7d, drift_ratio_30d, alert_triggered)
                VALUES (:ld, :m7, :m30, :dr7, :dr30, :alert)
            """),
            {
                "ld": log_date,
                "m7": mae_7d,
                "m30": mae_30d,
                "dr7": drift_ratio_7d,
                "dr30": drift_ratio_30d,
                "alert": alert_triggered,
            },
        )
        session.commit()
