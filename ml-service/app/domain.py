"""
FlowGuard ML Service — Shared domain types.
All dataclasses, enums and value objects used across the ML pipeline.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime
from enum import Enum
from typing import Optional

import numpy as np


# ── Enums ──────────────────────────────────────────────────────────────────────

class GapSeverity(str, Enum):
    MINOR    = "MINOR"       # ≤ 3 days  — weekend / holiday
    MODERATE = "MODERATE"    # 4–14 days — sync issue
    SEVERE   = "SEVERE"      # > 14 days — Nordigen outage


class QualityLabel(str, Enum):
    HIGH         = "HIGH"          # score > 0.8
    MEDIUM       = "MEDIUM"        # score > 0.5
    LOW          = "LOW"           # score > 0.2
    INSUFFICIENT = "INSUFFICIENT"  # score ≤ 0.2  → rules-only


class RecurringCategory(str, Enum):
    LOYER      = "LOYER"
    ABONNEMENT = "ABONNEMENT"
    URSSAF     = "URSSAF"
    TVA        = "TVA"
    MUTUELLE   = "MUTUELLE"
    ASSURANCE  = "ASSURANCE"
    ENERGIE    = "ENERGIE"
    AUTRE      = "AUTRE"


class RecurringFrequency(str, Enum):
    HIGHLY_RECURRING = "HIGHLY_RECURRING"  # std_dev_days < 3
    RECURRING        = "RECURRING"         # std_dev_days < 7
    NON_RECURRING    = "NON_RECURRING"


class UserProfile(str, Enum):
    SALARIED = "SALARIED"
    FREELANCE = "FREELANCE"
    ARTISAN   = "ARTISAN"
    MIXED     = "MIXED"
    UNKNOWN   = "UNKNOWN"


class ModelUsed(str, Enum):
    LSTM_ENSEMBLE = "LSTM_ENSEMBLE"
    PROPHET_RULES = "PROPHET_RULES"
    RULES_ONLY    = "RULES_ONLY"
    INSUFFICIENT  = "INSUFFICIENT"


class AlertSeverity(str, Enum):
    LOW      = "LOW"
    MEDIUM   = "MEDIUM"
    HIGH     = "HIGH"
    CRITICAL = "CRITICAL"


# ── Input types ────────────────────────────────────────────────────────────────

@dataclass
class Transaction:
    date:             date
    amount:           float       # positive = credit, negative = debit
    label:            str
    balance:          float = 0.0
    category:         Optional[str] = None
    is_recurring:     bool  = False
    creditor_debtor:  Optional[str] = None
    is_duplicate:     bool  = False


# ── Gap detection ──────────────────────────────────────────────────────────────

@dataclass
class GapReport:
    start:              date
    end:                date
    days:               int
    severity:           GapSeverity
    imputed:            bool = False
    imputation_method:  str  = "skip"  # 'zero' | 'linear' | 'ffill' | 'skip'


# ── Recurring patterns ─────────────────────────────────────────────────────────

@dataclass
class RecurringPattern:
    creditor:         str
    median_amount:    float
    interval_days:    int
    std_dev_days:     float
    confidence:       float
    next_occurrence:  date
    category:         RecurringCategory  = RecurringCategory.AUTRE
    frequency:        RecurringFrequency = RecurringFrequency.RECURRING


# ── User feature vector ────────────────────────────────────────────────────────

@dataclass
class UserFeatureVector:
    avg_monthly_income:       float
    income_volatility:        float   # std / mean of monthly incomes
    avg_monthly_expenses:     float
    expense_regularity:       float   # 1.0 = perfectly regular
    recurring_expense_ratio:  float   # recurring / total expenses
    estimated_profile:        UserProfile
    history_days:             int
    data_quality_score:       float


# ── Data quality ───────────────────────────────────────────────────────────────

@dataclass
class DataQualityScore:
    score:               float
    label:               QualityLabel
    severe_gap_count:    int
    moderate_gap_count:  int
    outliers_ratio:      float
    history_days:        int
    severe_gap_flag:     bool = False


# ── Pipeline result ────────────────────────────────────────────────────────────

@dataclass
class PipelineResult:
    cleaned_series:     "pd.DataFrame"   # type: ignore
    raw_series:         "pd.DataFrame"   # type: ignore
    gaps:               list[GapReport]
    recurring_patterns: list[RecurringPattern]
    fiscal_features:    "pd.DataFrame"   # type: ignore
    user_features:      UserFeatureVector
    quality_score:      DataQualityScore
    transactions:       list[Transaction]
    feature_matrix:     Optional["np.ndarray"] = None  # (seq_len, 15) — LSTM input


# ── Model outputs ──────────────────────────────────────────────────────────────

@dataclass
class UncertaintyResult:
    mean_prediction: "np.ndarray"
    std_prediction:  "np.ndarray"
    p5_prediction:   "np.ndarray"
    p25_prediction:  "np.ndarray"
    p75_prediction:  "np.ndarray"
    p95_prediction:  "np.ndarray"


@dataclass
class BaselinePrediction:
    daily_balances:    "np.ndarray"   # (horizon,)
    uncertainty_lower: "np.ndarray"
    uncertainty_upper: "np.ndarray"


@dataclass
class ProphetPrediction:
    daily_balances: "np.ndarray"   # (horizon,)
    ci_lower:       "np.ndarray"   # 80% CI lower
    ci_upper:       "np.ndarray"   # 80% CI upper


@dataclass
class EnsembleWeights:
    lstm:    float
    prophet: float
    rules:   float

    def __post_init__(self) -> None:
        total = self.lstm + self.prophet + self.rules
        assert abs(total - 1.0) < 1e-5, f"Weights must sum to 1.0, got {total:.4f}"


# ── Sanity check ───────────────────────────────────────────────────────────────

@dataclass
class SanityResult:
    passed:          bool
    failed_rules:    list[str] = field(default_factory=list)
    sanity_override: bool = False


# ── Prediction output ──────────────────────────────────────────────────────────

@dataclass
class DailyBalance:
    date:        date
    balance:     float
    balance_p25: Optional[float] = None
    balance_p75: Optional[float] = None


@dataclass
class CriticalPoint:
    date:               date
    predicted_balance:  float
    severity:           AlertSeverity
    cause:              str


@dataclass
class Anomaly:
    date:          date
    amount:        float
    outlier_type:  str    # 'high_income' | 'high_expense' | 'duplicate'
    outlier_score: float


@dataclass
class EnsemblePrediction:
    account_id:   str
    generated_at: datetime
    horizon_days: int

    # Daily predictions
    daily_balance: list[DailyBalance]

    # Summary
    min_balance:      float
    min_balance_date: date
    predicted_deficit: bool
    deficit_amount:   Optional[float]
    deficit_date:     Optional[date]

    # Confidence
    confidence_score:       float       # 0.0–1.0
    confidence_label:       str         # HIGH / MEDIUM / LOW / INSUFFICIENT
    mae_estimate:           float       # estimated MAE in euros
    uncertainty_band_width: float       # (p75-p25) mean

    # Metadata
    model_used:          ModelUsed
    history_days:        int
    data_quality:        DataQualityScore
    weights_used:        EnsembleWeights
    attention_highlights: list[date] = field(default_factory=list)

    # Alerts
    critical_points:    list[CriticalPoint]    = field(default_factory=list)
    anomalies_detected: list[Anomaly]          = field(default_factory=list)
    recurring_detected: list[RecurringPattern] = field(default_factory=list)

    # Sanity
    sanity_override: bool = False

    @property
    def metadata(self) -> "EnsemblePrediction":
        """Convenience accessor so tests can do result.metadata.sanity_override."""
        return self


# ── Training types ─────────────────────────────────────────────────────────────

@dataclass
class ModelVersion:
    version:           str
    mae_7d:            float
    mae_30d:           float
    mae_90d:           float
    deficit_recall:    float
    deficit_precision: float
    n_users_trained:   int
    model_path:        str
    status:            str = "CANDIDATE"   # CANDIDATE | ACTIVE | DEPRECATED
    created_at:        Optional[datetime] = None
    config:            dict = field(default_factory=dict)


@dataclass
class EvaluationReport:
    mae_7d:                 float
    mae_30d:                float
    mae_90d:                float
    deficit_recall:         float
    passes_production_threshold: bool = False
    mae_overall:            float = 0.0
    rmse_overall:           float = 0.0
    mape_overall:           float = 0.0
    direction_accuracy:     float = 0.0
    deficit_precision:      float = 0.0
    by_profile:             dict = field(default_factory=dict)
    by_history_bucket:      dict = field(default_factory=dict)
    segmented_mae:          dict = field(default_factory=dict)
    recommended_action:     str  = "INVESTIGATE"   # DEPLOY | RETRAIN | INVESTIGATE


@dataclass
class TrainingResult:
    version:            "ModelVersion"
    evaluation:         EvaluationReport
    promoted:           bool
    model_path:         str
    trained_at:         datetime
    n_users:            int
    train_losses:       list[float] = field(default_factory=list)
    val_losses:         list[float] = field(default_factory=list)


# ── Monitoring types ───────────────────────────────────────────────────────────

@dataclass
class DriftResult:
    psi_score:           float
    drift_label:         str    # NO_DRIFT | MODERATE_DRIFT | SIGNIFICANT_DRIFT | CRITICAL_DRIFT
    ks_statistic:        float
    ks_p_value:          float
    feature_psi:         dict = field(default_factory=dict)
    trigger_retrain:     bool = False
    detected_at:         Optional[datetime] = None


@dataclass
class ConceptDriftResult:
    drift_label:     str
    trigger_retrain: bool
    detected_at:     Optional[datetime] = None
    rolling_mae_7d:  Optional[float] = None
    rolling_mae_30d: Optional[float] = None
    baseline_mae:    Optional[float] = None
    drift_ratio_7d:  Optional[float] = None
    drift_ratio_30d: Optional[float] = None
    alert_severity:  Optional[AlertSeverity] = None
