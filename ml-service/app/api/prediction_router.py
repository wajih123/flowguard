"""
FlowGuard ML Service — v3 Prediction Router (TimesFM + LSTM Ensemble)

Endpoints:
  POST /v3/predict        — prédiction complète (TimesFM zero-shot + LSTM quand disponible)
  GET  /v3/predict/health — état du modèle + poids de l'ensemble
"""
from __future__ import annotations

import logging
import math
from typing import Optional

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field, field_validator

from app.models.ensemble_predictor import EnsemblePredictor

log = logging.getLogger(__name__)
router = APIRouter(prefix="/v3", tags=["ML v3 (TimesFM + LSTM)"])

# Singleton partagé pour le process uvicorn
_ensemble: Optional[EnsemblePredictor] = None


def get_ensemble() -> EnsemblePredictor:
    global _ensemble
    if _ensemble is None:
        _ensemble = EnsemblePredictor()
    return _ensemble


# ── Pydantic models ──────────────────────────────────────────────────────────

class PredictionRequest(BaseModel):
    """
    Requête de prédiction de trésorerie.
    Accepte soit des soldes journaliers directs (depuis daily_balances view)
    soit des transactions brutes Bridge.
    """

    account_id: str = Field(min_length=1, max_length=128)
    daily_balances: list[float] = Field(
        min_length=14,
        description="Soldes journaliers du plus ancien au plus récent",
    )
    dates: list[str] = Field(
        min_length=14,
        description="Dates correspondantes au format YYYY-MM-DD",
    )
    horizon_days: int = Field(default=30, ge=7, le=90)

    @field_validator("daily_balances")
    @classmethod
    def validate_balances(cls, v: list[float]) -> list[float]:
        for val in v:
            if math.isnan(val) or math.isinf(val):
                raise ValueError("daily_balances ne doit pas contenir NaN ou Inf")
        return v

    @field_validator("dates")
    @classmethod
    def validate_dates(cls, v: list[str]) -> list[str]:
        import re
        pattern = re.compile(r"^\d{4}-\d{2}-\d{2}$")
        for d in v:
            if not pattern.match(d):
                raise ValueError(f"Date invalide: {d} — format attendu YYYY-MM-DD")
        return v

    @field_validator("horizon_days")
    @classmethod
    def validate_horizon(cls, v: int) -> int:
        if v not in (7, 14, 30, 60, 90):
            raise ValueError("horizon_days doit être 7, 14, 30, 60 ou 90")
        return v


class DailyForecast(BaseModel):
    date: str
    balance: float
    p25: float
    p75: float


class PredictionResponse(BaseModel):
    account_id: str
    horizon_days: int
    daily_balances: list[DailyForecast]
    min_balance: float
    min_balance_date: str
    deficit_predicted: bool
    deficit_amount: Optional[float]
    deficit_date: Optional[str]
    confidence_score: float
    confidence_label: str           # "Fiable" | "Indicatif" | "Estimation"
    estimated_error_eur: float
    model_used: str


class EnsembleHealthResponse(BaseModel):
    status: str
    timesfm_loaded: bool
    timesfm_load_error: Optional[str]
    lstm_available: bool
    ensemble_weights: dict
    model_used_today: str


# ── Endpoints ────────────────────────────────────────────────────────────────

@router.post("/predict", response_model=PredictionResponse)
async def predict_cash_flow(request: PredictionRequest) -> PredictionResponse:
    """
    Prédit le solde futur à l'aide de TimesFM (zero-shot) en phase MVP.
    Bascule automatiquement vers l'ensemble TimesFM+LSTM quand le LSTM est disponible.

    Requiert minimum 14 jours d'historique.
    Supporte des horizons de 7 / 14 / 30 / 60 / 90 jours.
    """
    if len(request.daily_balances) != len(request.dates):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="daily_balances et dates doivent avoir la même longueur",
        )

    ensemble = get_ensemble()

    try:
        result = ensemble.predict(
            account_id=request.account_id,
            daily_balances=request.daily_balances,
            dates=request.dates,
            horizon_days=request.horizon_days,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))
    except Exception as exc:
        log.error("prediction_failed", account_id=request.account_id, error=str(exc))
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Erreur interne du service de prédiction",
        )

    return PredictionResponse(
        account_id=result.account_id,
        horizon_days=result.horizon_days,
        daily_balances=[DailyForecast(**d) for d in result.daily_balances],
        min_balance=result.min_balance,
        min_balance_date=result.min_balance_date,
        deficit_predicted=result.deficit_predicted,
        deficit_amount=result.deficit_amount,
        deficit_date=result.deficit_date,
        confidence_score=result.confidence_score,
        confidence_label=result.confidence_label,
        estimated_error_eur=result.estimated_error_eur,
        model_used=result.model_used,
    )


@router.get("/predict/health", response_model=EnsembleHealthResponse)
async def ensemble_health() -> EnsembleHealthResponse:
    """
    Retourne l'état de l'ensemble : modèles chargés, poids, modèle actif.
    """
    ensemble = get_ensemble()

    if ensemble.lstm is not None:
        model_today = (
            f"ensemble(timesfm={ensemble.timesfm_weight:.0%},"
            f"lstm={ensemble.lstm_weight:.0%})"
        )
    else:
        model_today = "timesfm_only"

    return EnsembleHealthResponse(
        status="ok",
        timesfm_loaded=ensemble.timesfm.is_loaded,
        timesfm_load_error=ensemble.timesfm._load_error,
        lstm_available=ensemble.lstm is not None,
        ensemble_weights={
            "timesfm": ensemble.timesfm_weight,
            "lstm": ensemble.lstm_weight,
        },
        model_used_today=model_today,
    )
