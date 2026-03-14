import structlog
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from pydantic import BaseModel, Field
from datetime import date
from model import TreasuryPredictor
from database import get_training_data
from cache import get_cached, set_cached
import asyncio

log = structlog.get_logger()

# ── Startup / shutdown ────────────────────────────────────
predictor: TreasuryPredictor | None = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global predictor
    log.info("FlowGuard ML Service starting...")

    # Ensure ML-specific tables exist (idempotent)
    try:
        from app.db import ensure_ml_tables
        ensure_ml_tables()
        log.info("ML tables ready")
    except Exception as e:
        log.warning(f"ml_tables_init_failed: {e}")

    predictor = TreasuryPredictor()
    log.info("Predictor ready", model_loaded=predictor.model is not None)

    # Start ML maintenance scheduler (v2)
    try:
        from app.training.scheduler import start_scheduler, stop_scheduler
        _scheduler = start_scheduler()
        log.info("ML scheduler started")
    except Exception as e:
        log.warning(f"scheduler_start_failed: {e}")
        _scheduler = None

    yield

    if _scheduler is not None:
        try:
            from app.training.scheduler import stop_scheduler
            stop_scheduler()
        except Exception:
            pass
    log.info("ML Service shutting down")

app = FastAPI(
    title="FlowGuard ML Service",
    version="1.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Nginx handles external CORS
    allow_methods=["*"],
    allow_headers=["*"]
)

# ── v2 ML router (ensemble + health gate) ────────────────
try:
    from app.api.predict import router as ml_router
    app.include_router(ml_router, prefix="/v2")
    log.info("v2 ML router registered")
except Exception as _e:
    log.warning("v2_router_registration_failed", error=str(_e))

# ── Pydantic models ───────────────────────────────────────

class DailyBalance(BaseModel):
    date:    date
    balance: float

class PredictionRequest(BaseModel):
    account_id:   str
    series:       list[DailyBalance] = Field(min_length=7)
    horizon_days: int = Field(default=30, ge=1, le=180)

class CriticalPoint(BaseModel):
    date:               str
    projected_balance:  float
    urgency:            str  # IMMINENT | UPCOMING
    days_until:         int

class AnomalyPoint(BaseModel):
    date:        str
    balance:     float
    z_score:     float
    message_fr:  str

class PredictionResponse(BaseModel):
    account_id:      str
    predictions:     list[DailyBalance]
    critical_points: list[CriticalPoint]
    anomalies:       list[AnomalyPoint]
    confidence:      float
    health_score:    int
    model_version:   str
    used_lstm:       bool

class ScenarioRequest(BaseModel):
    account_id:    str
    series:        list[DailyBalance]
    scenario_type: str    # LATE_PAYMENT | EXTRA_EXPENSE | EARLY_INVOICE
    amount:        float
    delay_days:    int = 0

class ScenarioResponse(BaseModel):
    account_id:              str
    baseline_predictions:    list[DailyBalance]
    impact_predictions:      list[DailyBalance]
    worst_deficit:           float
    days_until_impact:       int
    recommended_action_fr:   str

# ── Endpoints ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status":       "OK",
        "version":      "1.0.0",
        "model_loaded": predictor.model is not None if predictor else False
    }

@app.post("/predict", response_model=PredictionResponse)
async def predict(req: PredictionRequest):
    if not predictor:
        raise HTTPException(503, detail="Modèle non initialisé")

    # Check cache
    cache_key = f"ml:predict:{req.account_id}:{req.horizon_days}"
    cached = get_cached(cache_key)
    if cached:
        return PredictionResponse(**cached)

    try:
        series_dicts = [{"date": str(s.date), "balance": s.balance}
                        for s in req.series]

        predictions_raw, confidence = predictor.predict(series_dicts, req.horizon_days)
        anomalies      = predictor.detect_anomalies(series_dicts)
        critical       = predictor.detect_critical_points(predictions_raw)
        health_score   = predictor.compute_health_score(series_dicts, predictions_raw)

        response = PredictionResponse(
            account_id      = req.account_id,
            predictions     = [DailyBalance(**p) for p in predictions_raw],
            critical_points = [CriticalPoint(**c) for c in critical],
            anomalies       = [AnomalyPoint(**a) for a in anomalies],
            confidence      = round(confidence, 3),
            health_score    = health_score,
            model_version   = "1.0.0",
            used_lstm       = predictor.model is not None
        )

        # Cache for 2 hours
        set_cached(cache_key, response.model_dump(), ttl_seconds=7200)
        log.info("Prediction generated",
                 account_id=req.account_id,
                 horizon=req.horizon_days,
                 used_lstm=predictor.model is not None,
                 confidence=round(confidence, 3))

        return response

    except ValueError as e:
        raise HTTPException(422, detail=str(e))
    except Exception as e:
        log.error("Prediction failed", account_id=req.account_id, error=str(e))
        raise HTTPException(500, detail="Erreur interne de prédiction")

@app.post("/scenario", response_model=ScenarioResponse)
async def scenario(req: ScenarioRequest):
    if not predictor:
        raise HTTPException(503, detail="Modèle non initialisé")

    try:
        series_dicts = [{"date": str(s.date), "balance": s.balance}
                        for s in req.series]

        baseline_raw, _ = predictor.predict(series_dicts, 90)

        # Apply scenario delta
        import copy
        from datetime import date as date_type, timedelta

        impact_raw = copy.deepcopy(baseline_raw)
        today = date_type.today()

        for i, p in enumerate(impact_raw):
            pred_date  = date_type.fromisoformat(p["date"])
            days_ahead = (pred_date - today).days

            if req.scenario_type == "LATE_PAYMENT":
                if days_ahead <= req.delay_days:
                    p["balance"] -= req.amount
            elif req.scenario_type == "EXTRA_EXPENSE":
                p["balance"] -= req.amount  # Permanent reduction
            elif req.scenario_type == "EARLY_INVOICE":
                if i == 0:
                    p["balance"] += req.amount  # One-time increase

        # Analysis
        worst = min((float(p["balance"]) for p in impact_raw), default=0.0)
        days_until = next(
            ((date_type.fromisoformat(p["date"]) - today).days
             for p in impact_raw if float(p["balance"]) < 0),
            -1
        )

        if worst < 0:
            action_fr = (
                f"Activez une Réserve FlowGuard de {abs(worst):.0f}€ "
                f"pour couvrir ce gap de trésorerie."
            )
        else:
            action_fr = "✅ Votre trésorerie reste positive dans ce scénario."

        return ScenarioResponse(
            account_id            = req.account_id,
            baseline_predictions  = [DailyBalance(**p) for p in baseline_raw],
            impact_predictions    = [DailyBalance(**p) for p in impact_raw],
            worst_deficit         = round(worst, 2),
            days_until_impact     = days_until,
            recommended_action_fr = action_fr
        )

    except ValueError as e:
        raise HTTPException(422, detail=str(e))
    except Exception as e:
        log.error("Scenario failed", error=str(e))
        raise HTTPException(500, detail="Erreur interne du simulateur")

@app.post("/retrain")
async def retrain(background_tasks=None):
    """
    Trigger model retraining on latest aggregated data.
    Runs in background — returns immediately.
    """
    async def run_training():
        try:
            log.info("Fetching training data from DB...")
            data = get_training_data(min_days=90)
            log.info("Training data loaded", accounts=len(data))
            metrics = predictor.train(data, epochs=50)
            log.info("Training complete", **metrics)
            # Invalidate all prediction caches
        except Exception as e:
            log.error("Training failed", error=str(e))

    asyncio.create_task(run_training())
    return {"status": "QUEUED", "message": "Ré-entraînement démarré en arrière-plan."}
