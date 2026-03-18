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


# ── Forecast accuracy reconciliation ─────────────────────────────────────────

class ReconcileRequest(BaseModel):
    """
    Reçu par le scheduler Java après que la solde réel est connu.
    Le backend insère déjà les lignes forecast_accuracy_log ; cet endpoint
    calcule le MAE et renvoie les métriques pour audit / monitoring.
    """
    account_id: str = Field(min_length=1, max_length=128)
    forecast_date: str = Field(description="YYYY-MM-DD — date à laquelle la prévision a été émise")
    horizon_days: int = Field(ge=1, le=90)
    predicted_balance: float
    actual_balance: float


class ReconcileResponse(BaseModel):
    account_id: str
    forecast_date: str
    horizon_days: int
    mae: float
    accuracy_pct: float
    drift_direction: str   # "OVER_ESTIMATED" | "UNDER_ESTIMATED" | "ACCURATE"


@router.post("/forecast-accuracy/reconcile", response_model=ReconcileResponse)
async def reconcile_forecast(req: ReconcileRequest) -> ReconcileResponse:
    """
    Reçoit le solde réel pour une prévision passée, calcule le MAE et la
    précision relative. Appelé par un scheduled job côté backend Java.

    drift_direction:
      - OVER_ESTIMATED  : prévu > réel  (trésorerie surestimée, situation pire que prévue)
      - UNDER_ESTIMATED : prévu < réel  (trésorerie sous-estimée, situation meilleure que prévue)
      - ACCURATE        : écart relatif < 2 %
    """
    mae = abs(req.predicted_balance - req.actual_balance)

    # Relative absolute error capped to avoid division-by-zero
    denominator = max(abs(req.actual_balance), 1.0)
    rel_error = mae / denominator
    accuracy_pct = max(0.0, round((1.0 - rel_error) * 100, 2))

    relative_threshold = 0.02  # 2 % tolerance considered "accurate"
    if rel_error <= relative_threshold:
        drift = "ACCURATE"
    elif req.predicted_balance > req.actual_balance:
        drift = "OVER_ESTIMATED"
    else:
        drift = "UNDER_ESTIMATED"

    log.info(
        "forecast_reconciled",
        account_id=req.account_id,
        forecast_date=req.forecast_date,
        horizon_days=req.horizon_days,
        mae=round(mae, 2),
        accuracy_pct=accuracy_pct,
        drift=drift,
    )

    return ReconcileResponse(
        account_id=req.account_id,
        forecast_date=req.forecast_date,
        horizon_days=req.horizon_days,
        mae=round(mae, 2),
        accuracy_pct=accuracy_pct,
        drift_direction=drift,
    )


# ── Forecast narrative explanation ───────────────────────────────────────────

class ExplainRequest(BaseModel):
    account_id: str = Field(min_length=1, max_length=128)
    daily_balances: list[float] = Field(min_length=14, max_length=365)
    dates: list[str] = Field(min_length=14, max_length=365)
    horizon_days: int = Field(default=30, ge=7, le=90)


class BalanceDriver(BaseModel):
    label: str          # e.g. "Charges récurrentes clusterisées"
    impact_eur: float   # approximate impact on the balance
    insight: str        # human-readable French sentence


class ExplainResponse(BaseModel):
    account_id: str
    horizon_days: int
    score_trend: str            # "IMPROVING" | "STABLE" | "DETERIORATING"
    main_drivers: list[BalanceDriver]
    summary: str                # 1–2 sentence French narrative


@router.post("/explain", response_model=ExplainResponse)
async def explain_forecast(req: ExplainRequest) -> ExplainResponse:
    """
    Génère une explication narrative en français du score de prévision actuel
    par rapport à la fenêtre précédente équivalente.

    Utilise une heuristique statistique légère (pas de LLM requis) :
      - Tendance du solde moyen sur la fenêtre historique
      - Détection de clusters de charges récurrentes
      - Variance anormale (indicateur de revenus irréguliers)
    """
    if len(req.daily_balances) != len(req.dates):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="daily_balances et dates doivent avoir la même longueur",
        )

    balances = req.daily_balances
    n = len(balances)

    # ── 1. Trend: compare first-half mean to second-half mean ──────────────
    mid = n // 2
    first_half_mean = sum(balances[:mid]) / mid
    second_half_mean = sum(balances[mid:]) / (n - mid)
    drift = second_half_mean - first_half_mean
    rel_drift = drift / max(abs(first_half_mean), 1.0)

    if rel_drift > 0.05:
        score_trend = "IMPROVING"
        trend_label = "amélioration"
    elif rel_drift < -0.05:
        score_trend = "DETERIORATING"
        trend_label = "dégradation"
    else:
        score_trend = "STABLE"
        trend_label = "stabilité"

    # ── 2. Daily drops — identify large outflows ────────────────────────────
    drops = [(i, balances[i] - balances[i - 1]) for i in range(1, n)]
    large_drops = sorted(
        [(i, d) for i, d in drops if d < -200],
        key=lambda x: x[1],
    )[:3]

    # ── 3. Recurring cluster detection (weekly pattern) ────────────────────
    weekly_drops = sum(1 for i, d in drops if d < -50 and i % 7 in (0, 1, 6))
    has_cluster = weekly_drops >= 2

    # ── 4. Variance check ──────────────────────────────────────────────────
    mean_b = sum(balances) / n
    variance = sum((b - mean_b) ** 2 for b in balances) / n
    cv = (variance ** 0.5) / max(abs(mean_b), 1.0)  # coefficient of variation
    high_variance = cv > 0.3

    # ── 5. Build drivers ───────────────────────────────────────────────────
    drivers: list[BalanceDriver] = []

    if large_drops:
        total_drop = sum(abs(d) for _, d in large_drops)
        drivers.append(BalanceDriver(
            label="Sorties importantes détectées",
            impact_eur=-total_drop,
            insight=(
                f"{len(large_drops)} sortie(s) importante(s) représentant "
                f"{total_drop:,.0f} € au total sur la période analysée."
            ),
        ))

    if has_cluster:
        drivers.append(BalanceDriver(
            label="Charges récurrentes hebdomadaires",
            impact_eur=-abs(sum(d for _, d in drops if d < 0) / max(n / 7, 1)),
            insight=(
                "Des charges récurrentes sont détectées en début ou fin de semaine "
                "(loyer, abonnements, salaires). Anticipez ces sorties dans votre budget."
            ),
        ))

    if high_variance:
        drivers.append(BalanceDriver(
            label="Revenus irréguliers",
            impact_eur=0.0,
            insight=(
                "Votre solde présente une forte variabilité (CV > 30 %), "
                "signe de revenus irréguliers. Constituez une réserve de précaution."
            ),
        ))

    if drift > 500:
        drivers.append(BalanceDriver(
            label="Tendance haussière du solde",
            impact_eur=drift,
            insight=f"Votre solde moyen a progressé de {drift:,.0f} € sur la période, signe positif.",
        ))
    elif drift < -500:
        drivers.append(BalanceDriver(
            label="Érosion du solde",
            impact_eur=drift,
            insight=(
                f"Votre solde moyen a reculé de {abs(drift):,.0f} € sur la période. "
                "Vérifiez vos charges fixes et pilotez vos encaissements."
            ),
        ))

    # Default driver when nothing noteworthy
    if not drivers:
        drivers.append(BalanceDriver(
            label="Flux équilibrés",
            impact_eur=0.0,
            insight="Aucune anomalie détectée. Vos flux entrants et sortants sont bien équilibrés.",
        ))

    # ── 6. Summary ─────────────────────────────────────────────────────────
    summary = (
        f"Sur les {n} derniers jours, votre trésorerie est en {trend_label} "
        f"({'+ ' if drift >= 0 else ''}{drift:,.0f} € de drift). "
    )
    if large_drops:
        summary += (
            f"Des sorties significatives ({len(large_drops)} opération(s)) "
            "ont pesé sur le solde. "
        )
    summary += (
        f"La prévision à {req.horizon_days} jours tient compte de ces tendances "
        "pour estimer votre solde futur."
    )

    return ExplainResponse(
        account_id=req.account_id,
        horizon_days=req.horizon_days,
        score_trend=score_trend,
        main_drivers=drivers,
        summary=summary,
    )

