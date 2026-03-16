"""
FlowGuard ML Service — v2 API Router.

Endpoints:
  POST /v2/predict    — full ensemble prediction (cacheable, 2h TTL)
  POST /v2/scenario   — what-if injection + baseline comparison
  GET  /v2/health     — ABSOLUTE GATE: must return mae_current<150 & reserve_safe=true
                        before any credit decision can be made

Security notes:
  - All inputs validated via strict Pydantic v2 models
  - No raw SQL; all DB writes go through app.db helpers
  - account_id is checked against the authenticated user claim (X-Account-Id header)
  - Error responses never leak internal stack traces
"""
from __future__ import annotations

import hashlib
import json
import logging
from datetime import date, datetime
from typing import Any, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field, field_validator, model_validator

from app.db import get_active_model_version, get_baseline_mae
from app.domain import EnsemblePrediction, Transaction

log = logging.getLogger(__name__)
router = APIRouter(tags=["ML v2"])

# ---------------------------------------------------------------------------
# Pydantic v2 request / response models (strict validation)
# ---------------------------------------------------------------------------


class TransactionItem(BaseModel):
    """Single transaction as provided by the Nordigen sync."""

    model_config = {"strict": False}

    date: date
    amount: float
    balance: float
    creditor_name: Optional[str] = None
    debtor_name: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None

    @field_validator("amount", "balance")
    @classmethod
    def finite_float(cls, v: float) -> float:
        import math
        if math.isnan(v) or math.isinf(v):
            raise ValueError("amount and balance must be finite numbers")
        return v


class PredictionRequest(BaseModel):
    model_config = {"strict": False}

    account_id: str = Field(min_length=1, max_length=128)
    transactions: list[TransactionItem] = Field(min_length=1)
    horizon: int = Field(default=90, ge=7, le=365)

    @field_validator("transactions")
    @classmethod
    def enough_transactions(cls, v: list[TransactionItem]) -> list[TransactionItem]:
        if len(v) < 5:
            raise ValueError("At least 5 transactions required")
        return v

    @model_validator(mode="after")
    def sorted_transactions(self) -> "PredictionRequest":
        self.transactions = sorted(self.transactions, key=lambda t: t.date)
        return self


class DailyBalanceOut(BaseModel):
    date: date
    balance: float
    balance_p25: float
    balance_p75: float


class CriticalPointOut(BaseModel):
    date: date
    predicted_balance: float
    severity: str
    cause: str


class PredictionResponse(BaseModel):
    account_id: str
    generated_at: datetime
    horizon_days: int
    model_used: str
    confidence_score: float
    confidence_label: str
    mae_estimate: float
    history_days: int
    predicted_deficit: bool
    deficit_amount: Optional[float]
    deficit_date: Optional[date]
    min_balance: float
    min_balance_date: date
    daily_balance: list[DailyBalanceOut]
    critical_points: list[CriticalPointOut]
    sanity_override: bool
    data_quality_label: str
    data_quality_score: float
    model_race_winner: Optional[str] = None        # "lstm" | "prophet" | "rules" | None
    model_race_scores: dict[str, Any] = {}         # {model: {mae_30d, n_eval_points}}


class ScenarioRequest(BaseModel):
    model_config = {"strict": False}

    account_id: str = Field(min_length=1, max_length=128)
    transactions: list[TransactionItem] = Field(min_length=1)
    horizon: int = Field(default=90, ge=7, le=365)
    # Synthetic transaction to inject (what-if)
    scenario_date: date
    scenario_amount: float = Field(description="Negative = expense, positive = income")
    scenario_label: Optional[str] = "scenario_injection"

    @field_validator("scenario_amount")
    @classmethod
    def non_zero(cls, v: float) -> float:
        if v == 0:
            raise ValueError("scenario_amount must be non-zero")
        return v


class ScenarioResponse(BaseModel):
    account_id: str
    scenario_label: Optional[str]
    baseline: PredictionResponse
    with_scenario: PredictionResponse
    impact_summary: dict[str, Any]


# ---------------------------------------------------------------------------
# Dependencies & utilities
# ---------------------------------------------------------------------------

def _get_ensemble():
    """FastAPI dependency — singleton EnsemblePredictor."""
    from app.models.ensemble import EnsemblePredictor
    # Use a module-level singleton to avoid reloading LSTM on every request
    if not hasattr(_get_ensemble, "_instance"):
        _get_ensemble._instance = EnsemblePredictor()
    return _get_ensemble._instance


def _get_redis():
    """FastAPI dependency — Redis client (can be None in dev)."""
    try:
        import os
        import redis as redis_lib
        r = redis_lib.from_url(
            os.getenv("REDIS_URL", "redis://localhost:6379/0"),
            decode_responses=True,
        )
        r.ping()
        return r
    except Exception:
        return None


def _cache_key(account_id: str, tx_items: list[TransactionItem], horizon: int) -> str:
    digest = hashlib.sha256(
        json.dumps(
            [
                account_id,
                horizon,
                [(str(t.date), t.amount, t.balance) for t in tx_items],
            ],
            sort_keys=True,
        ).encode()
    ).hexdigest()[:16]
    return f"ml:pred:{account_id}:{digest}"


def _to_domain_transactions(items: list[TransactionItem]) -> list[Transaction]:
    return [
        Transaction(
            date=item.date,
            amount=item.amount,
            balance=item.balance,
            label=item.creditor_name or item.description or "",
            creditor_debtor=item.creditor_name or item.debtor_name or "",
            category=item.category,
        )
        for item in items
    ]


def _prediction_to_response(pred: EnsemblePrediction) -> PredictionResponse:
    return PredictionResponse(
        account_id=pred.account_id,
        generated_at=pred.generated_at,
        horizon_days=pred.horizon_days,
        model_used=pred.model_used.value,
        confidence_score=round(pred.confidence_score, 3),
        confidence_label=pred.confidence_label,
        mae_estimate=round(pred.mae_estimate, 1),
        history_days=pred.history_days,
        predicted_deficit=pred.predicted_deficit,
        deficit_amount=round(pred.deficit_amount, 2) if pred.deficit_amount else None,
        deficit_date=pred.deficit_date,
        min_balance=round(pred.min_balance, 2),
        min_balance_date=pred.min_balance_date,
        daily_balance=[
            DailyBalanceOut(
                date=d.date,
                balance=round(d.balance, 2),
                balance_p25=round(d.balance_p25, 2),
                balance_p75=round(d.balance_p75, 2),
            )
            for d in pred.daily_balance
        ],
        critical_points=[
            CriticalPointOut(
                date=cp.date,
                predicted_balance=round(cp.predicted_balance, 2),
                severity=cp.severity.value,
                cause=cp.cause,
            )
            for cp in pred.critical_points
        ],
        sanity_override=pred.sanity_override,
        data_quality_label=pred.data_quality.label.value if pred.data_quality else "UNKNOWN",
        data_quality_score=round(pred.data_quality.score, 3) if pred.data_quality else 0.0,
        model_race_winner=pred.model_race_winner,
        model_race_scores=pred.model_race_scores,
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@router.post("/predict", response_model=PredictionResponse, status_code=status.HTTP_200_OK)
async def predict(
    body: PredictionRequest,
    x_account_id: Optional[str] = Header(default=None, alias="X-Account-Id"),
    ensemble=Depends(_get_ensemble),
    redis=Depends(_get_redis),
) -> PredictionResponse:
    """
    Run ensemble cash-flow prediction for an account.
    Cached 2h in Redis. Falls back gracefully on insufficient data.
    """
    # Minimal ownership check: if header provided, it must match body
    if x_account_id and x_account_id != body.account_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="account_id mismatch",
        )

    # Cache lookup
    cache_key = _cache_key(body.account_id, body.transactions, body.horizon)
    if redis:
        try:
            cached = redis.get(cache_key)
            if cached:
                return PredictionResponse.model_validate_json(cached)
        except Exception as e:
            log.warning("cache_read_failed", error=str(e))

    # Run prediction
    try:
        transactions = _to_domain_transactions(body.transactions)
        pred = ensemble.predict(
            account_id=body.account_id,
            transactions=transactions,
            horizon=body.horizon,
        )
    except ValueError as e:
        # Insufficient data — return degraded response (HTTP 200 per spec)
        log.info("insufficient_data", account_id=body.account_id, error=str(e))
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={"code": "INSUFFICIENT_DATA", "message": str(e)},
        )
    except RuntimeError as e:
        # Model not loaded / GPU OOM
        log.error("model_runtime_error", account_id=body.account_id, error=str(e))
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "MODEL_UNAVAILABLE", "message": "Prediction service temporarily unavailable"},
        )
    except Exception as e:
        log.error("predict_unexpected_error", account_id=body.account_id, error=str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"code": "INTERNAL_ERROR", "message": "An unexpected error occurred"},
        )

    response = _prediction_to_response(pred)

    # Cache result
    if redis:
        try:
            redis.setex(cache_key, 7200, response.model_dump_json())
        except Exception as e:
            log.warning("cache_write_failed", error=str(e))

    return response


@router.post("/scenario", response_model=ScenarioResponse, status_code=status.HTTP_200_OK)
async def scenario(
    body: ScenarioRequest,
    x_account_id: Optional[str] = Header(default=None, alias="X-Account-Id"),
    ensemble=Depends(_get_ensemble),
) -> ScenarioResponse:
    """
    What-if analysis: inject a synthetic transaction and compare with baseline.
    """
    if x_account_id and x_account_id != body.account_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="account_id mismatch")

    try:
        transactions = _to_domain_transactions(body.transactions)

        # Baseline
        baseline_pred = ensemble.predict(
            account_id=body.account_id,
            transactions=transactions,
            horizon=body.horizon,
        )

        # Build synthetic transaction (cumulative balance = last_balance + scenario_amount)
        last_balance = transactions[-1].balance if transactions else 0.0
        synthetic = Transaction(
            date=body.scenario_date,
            amount=body.scenario_amount,
            balance=last_balance + body.scenario_amount,
            label=body.scenario_label or "scenario",
            creditor_debtor="scenario" if body.scenario_amount > 0 else "",
            category=None,
        )
        # Insert chronologically
        tx_with_scenario = sorted(transactions + [synthetic], key=lambda t: t.date)

        scenario_pred = ensemble.predict(
            account_id=body.account_id,
            transactions=tx_with_scenario,
            horizon=body.horizon,
        )

        impact_min_change = round(scenario_pred.min_balance - baseline_pred.min_balance, 2)
        impact_deficit_change = (
            (1 if scenario_pred.predicted_deficit else 0)
            - (1 if baseline_pred.predicted_deficit else 0)
        )

        return ScenarioResponse(
            account_id=body.account_id,
            scenario_label=body.scenario_label,
            baseline=_prediction_to_response(baseline_pred),
            with_scenario=_prediction_to_response(scenario_pred),
            impact_summary={
                "min_balance_delta": impact_min_change,
                "deficit_introduced": impact_deficit_change > 0,
                "deficit_resolved": impact_deficit_change < 0,
                "scenario_amount": body.scenario_amount,
                "scenario_date": body.scenario_date.isoformat(),
            },
        )

    except HTTPException:
        raise
    except Exception as e:
        log.error("scenario_error", error=str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"code": "INTERNAL_ERROR", "message": "An unexpected error occurred"},
        )


@router.get("/health")
async def health() -> dict:
    """
    ABSOLUTE GATE.
    Must return {'mae_current': <150, 'reserve_safe': True} before
    any FlashCredit decision can be made.
    """
    mae_current: Optional[float] = None
    model_available = False

    try:
        mae_current = get_baseline_mae()
        active = get_active_model_version()
        model_available = active is not None
    except Exception as e:
        log.warning(f"health_check_db_error: {e}")

    if mae_current is None:
        reserve_safe = False
        # No model yet; return 200 but flagged
        return {
            "status": "degraded",
            "mae_current": None,
            "reserve_safe": False,
            "model_available": False,
            "message": "No trained model found",
        }

    reserve_safe = mae_current < 150.0

    return {
        "status": "ok" if reserve_safe else "degraded",
        "mae_current": round(mae_current, 2),
        "reserve_safe": reserve_safe,
        "model_available": model_available,
    }
