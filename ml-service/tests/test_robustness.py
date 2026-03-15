"""
Adversarial robustness tests for FlowGuard ML service.

10 tests covering:
  1.  cold_start               — fewer than 30d of data
  2.  all_zeros                — all amounts are 0
  3.  severe_gap               — 20-day data gap
  4.  extreme_volatility       — ×20 standard deviation
  5.  urssaf_detection         — URSSAF recurring must be detected
  6.  duplicates               — duplicate transactions must be flagged
  7.  lstm_unavailable         — graceful fallback when LSTM not loaded
  8.  sanity_catches_hallucination — impossible jump triggers override
  9.  concurrent_predictions   — 50 concurrent predict calls
  10. drift_triggers_retrain   — concept drift > 2× baseline MAE

Run with:  pytest tests/test_robustness.py -v
"""
from __future__ import annotations

import asyncio
import random
from datetime import date, timedelta
from typing import Optional
from unittest.mock import MagicMock, patch

import numpy as np
import pytest

from app.domain import (
    EnsemblePrediction,
    ModelUsed,
    QualityLabel,
    RecurringCategory,
    Transaction,
    UncertaintyResult,
)
from app.models.ensemble import EnsemblePredictor, SanityChecker


# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────


def generate_series(
    days: int = 180,
    base_balance: float = 5000.0,
    volatility: float = 200.0,
    all_zeros: bool = False,
    gap_start: Optional[int] = None,
    gap_days: int = 0,
) -> list[Transaction]:
    """
    Generate a synthetic daily transaction series.
    gap_start: day index at which to insert a gap.
    """
    rng = random.Random(42)
    start = date.today() - timedelta(days=days)
    txs: list[Transaction] = []
    balance = base_balance

    for i in range(days):
        if gap_start is not None and gap_start <= i < gap_start + gap_days:
            continue  # create a gap

        d = start + timedelta(days=i)
        if all_zeros:
            amount = 0.0
        else:
            amount = rng.gauss(0, volatility)
        balance = max(balance + amount, -50000.0)

        txs.append(
            Transaction(
                date=d,
                amount=amount,
                balance=balance,
                label="test_transaction",
                creditor_debtor="test_creditor" if amount < 0 else "",
            )
        )
    return txs


def generate_freelancer_series(
    months: int = 6,
    urssaf_amount: float = 800.0,
    urssaf_regularity: float = 1.0,
) -> list[Transaction]:
    """
    Generate a freelancer series with regular URSSAF-like withdrawals
    on the 15th of Feb/May/Aug/Nov.
    """
    rng = random.Random(99)
    start_date = date.today() - timedelta(days=months * 30)
    txs: list[Transaction] = []
    balance = 8000.0
    d = start_date

    while d <= date.today():
        # Regular income (variable)
        if d.day == 1:
            income = rng.gauss(3500, 700)
            balance += income
            txs.append(
                Transaction(
                    date=d,
                    amount=income,
                    balance=balance,
                    label="virement client",
                    creditor_debtor="client_abc",
                )
            )

        # URSSAF on Feb/May/Aug/Nov 15th
        if d.day == 15 and d.month in {2, 5, 8, 11}:
            if rng.random() <= urssaf_regularity:
                urssaf = -abs(rng.gauss(urssaf_amount, 50))
                balance += urssaf
                txs.append(
                    Transaction(
                        date=d,
                        amount=urssaf,
                        balance=balance,
                        label="URSSAF cotisations",
                        creditor_debtor="urssaf",
                    )
                )

        # Daily expenses
        expense = -abs(rng.gauss(100, 30))
        balance += expense
        txs.append(
            Transaction(
                date=d,
                amount=expense,
                balance=balance,
                label="daily expense",
                creditor_debtor="",
            )
        )
        d += timedelta(days=1)

    return sorted(txs, key=lambda t: t.date)


def _mock_ensemble_no_lstm() -> EnsemblePredictor:
    """EnsemblePredictor with LSTM explicitly set to None."""
    predictor = EnsemblePredictor.__new__(EnsemblePredictor)
    from app.data.pipeline import DataQualityPipeline
    from app.models.baseline_model import ProphetStyleDecomposer, RuleBasedPredictor
    from app.models.ensemble import SanityChecker
    from app.models.model_race import ModelRaceEvaluator

    from app.models.timesfm_predictor import TimesFMPredictor

    predictor._lstm = None
    predictor._prophet = ProphetStyleDecomposer()
    predictor._rules = RuleBasedPredictor()
    predictor._pipeline = DataQualityPipeline()
    predictor._sanity = SanityChecker()
    predictor._race = ModelRaceEvaluator()
    predictor._timesfm = TimesFMPredictor()  # not loaded — is_loaded=False
    return predictor


# ─────────────────────────────────────────────────────────────────────────────
# Test 1 — Cold start (< 30 days)
# ─────────────────────────────────────────────────────────────────────────────

def test_cold_start_uses_rules_only():
    """With < 30 days of history the system must fall back to RULES_ONLY."""
    transactions = generate_series(days=15)
    predictor = _mock_ensemble_no_lstm()
    result = predictor.predict("user_cold", transactions, horizon=30)

    assert result is not None
    assert result.model_used in (ModelUsed.RULES_ONLY, ModelUsed.INSUFFICIENT), (
        f"Expected RULES_ONLY or INSUFFICIENT, got {result.model_used}"
    )
    assert result.data_quality.label == QualityLabel.INSUFFICIENT, (
        f"Expected INSUFFICIENT data quality, got {result.data_quality.label}"
    )
    # Must not crash; must return a valid 30-day forecast
    assert len(result.daily_balance) == 30


# ─────────────────────────────────────────────────────────────────────────────
# Test 2 — All-zeros series
# ─────────────────────────────────────────────────────────────────────────────

def test_all_zeros_no_crash():
    """A series of all-zero transactions must return a flat prediction."""
    transactions = generate_series(days=120, all_zeros=True)
    predictor = _mock_ensemble_no_lstm()
    result = predictor.predict("user_zeros", transactions, horizon=90)

    assert result is not None
    assert not np.any(np.isnan([d.balance for d in result.daily_balance]))
    assert not np.any(np.isinf([d.balance for d in result.daily_balance]))


# ─────────────────────────────────────────────────────────────────────────────
# Test 3 — Severe gap (20-day gap)
# ─────────────────────────────────────────────────────────────────────────────

def test_severe_gap_handled():
    """A 20-day gap must be detected as SEVERE and NOT cause a crash."""
    from app.data.pipeline import DataQualityPipeline
    from app.domain import GapSeverity

    transactions = generate_series(days=180, gap_start=60, gap_days=20)
    pipeline = DataQualityPipeline()
    import pandas as pd

    df = pd.DataFrame(
        [{"date": t.date, "amount": t.amount, "balance": t.balance} for t in transactions]
    )
    df["date"] = pd.to_datetime(df["date"])
    gaps = pipeline.detect_gaps(df)

    severe = [g for g in gaps if g.severity == GapSeverity.SEVERE]
    assert len(severe) >= 1, "Expected at least one SEVERE gap"
    assert severe[0].days >= 20

    # Prediction must still work
    predictor = _mock_ensemble_no_lstm()
    result = predictor.predict("user_gap", transactions, horizon=90)
    assert result is not None
    assert len(result.daily_balance) == 90


# ─────────────────────────────────────────────────────────────────────────────
# Test 4 — Extreme volatility
# ─────────────────────────────────────────────────────────────────────────────

def test_extreme_volatility_no_crash():
    """Very high volatility (×20 std) must return a bounded prediction."""
    transactions = generate_series(days=120, volatility=20_000.0)
    predictor = _mock_ensemble_no_lstm()
    result = predictor.predict("user_volatile", transactions, horizon=30)

    assert result is not None
    balances = [d.balance for d in result.daily_balance]
    assert not any(np.isinf(balances)), "Infinity in prediction output"
    assert not any(np.isnan(balances)), "NaN in prediction output"


# ─────────────────────────────────────────────────────────────────────────────
# Test 5 — URSSAF detection (BLOCKING)
# ─────────────────────────────────────────────────────────────────────────────

def test_urssaf_detected_in_recurring():
    """
    BLOCKING TEST — URSSAF payments must be detected in recurring patterns.
    Freelancer series with payments on Feb/May/Aug/Nov 15th.
    """
    from app.data.pipeline import DataQualityPipeline

    transactions = generate_freelancer_series(months=12, urssaf_amount=800.0)
    pipeline = DataQualityPipeline()
    recurring = pipeline.detect_recurring(transactions)

    urssaf_patterns = [p for p in recurring if p.category == RecurringCategory.URSSAF]
    assert len(urssaf_patterns) >= 1, (
        f"URSSAF not detected! Found categories: {[p.category.value for p in recurring]}"
    )
    # URSSAF amounts should be in expected range
    for p in urssaf_patterns:
        assert 200 <= p.median_amount <= 5000, f"URSSAF amount out of range: {p.median_amount}"


# ─────────────────────────────────────────────────────────────────────────────
# Test 6 — Duplicate transactions
# ─────────────────────────────────────────────────────────────────────────────

def test_duplicates_flagged():
    """
    Exact duplicate transactions (same amount, same creditor, within 24h)
    must be flagged by the pipeline.
    """
    from app.data.pipeline import DataQualityPipeline

    d0 = date.today() - timedelta(days=90)
    base_txs = generate_series(days=90)

    # Inject a duplicate pair
    dup_date = d0 + timedelta(days=45)
    dup = Transaction(
        date=dup_date,
        amount=-500.0,
        balance=3000.0,
        label="duplicate payment",
        creditor_debtor="client_duplicate",
    )
    base_txs.append(dup)  # original already exists with similar amount from generate_series? No — let's add two
    dup2 = Transaction(
        date=dup_date,  # same day
        amount=-500.0,
        balance=2500.0,
        label="duplicate payment",
        creditor_debtor="client_duplicate",
    )
    base_txs.append(dup2)
    base_txs_sorted = sorted(base_txs, key=lambda t: t.date)

    import pandas as pd

    df = pd.DataFrame(
        [
            {
                "date": t.date,
                "amount": t.amount,
                "balance": t.balance,
                "creditor_debtor": t.creditor_debtor,
            }
            for t in base_txs_sorted
        ]
    )
    df["date"] = pd.to_datetime(df["date"])

    pipeline = DataQualityPipeline()
    tagged = pipeline.detect_outliers(df)

    duplicates = tagged[tagged["is_duplicate"] == True]  # noqa: E712
    assert len(duplicates) >= 1, "Expected at least one duplicate to be flagged"


# ─────────────────────────────────────────────────────────────────────────────
# Test 7 — LSTM unavailable (graceful fallback)
# ─────────────────────────────────────────────────────────────────────────────

def test_lstm_unavailable_fallback():
    """
    When LSTM model is not loaded, the ensemble must fall back to
    PROPHET_RULES or RULES_ONLY gracefully (no exception).
    """
    transactions = generate_series(days=180)
    predictor = _mock_ensemble_no_lstm()  # LSTM = None

    result = predictor.predict("user_no_lstm", transactions, horizon=90)

    assert result is not None
    assert result.model_used != ModelUsed.LSTM_ENSEMBLE, (
        "Should NOT use LSTM_ENSEMBLE when LSTM is unavailable"
    )
    assert len(result.daily_balance) == 90
    # Weights must sum to ~1.0 (or within epsilon)
    total_w = result.weights_used.lstm + result.weights_used.prophet + result.weights_used.rules
    assert abs(total_w - 1.0) < 1e-4, f"Weights don't sum to 1: {total_w}"


# ─────────────────────────────────────────────────────────────────────────────
# Test 8 — Sanity checker catches hallucination / impossible jump
# ─────────────────────────────────────────────────────────────────────────────

def test_sanity_catches_hallucination():
    """
    A prediction with an impossible +500 000€ jump must be caught by
    SanityChecker, triggering a fallback (sanity_override=True).
    """
    checker = SanityChecker()
    current_balance = 5000.0
    # Hallucinated prediction: sudden +500k jump
    hallucination = np.full(90, current_balance)
    hallucination[5] = 500_000.0  # Impossible jump

    result = checker.validate(
        prediction=hallucination,
        current_balance=current_balance,
        avg_daily_volume=300.0,
        avg_monthly_income=3000.0,
        avg_monthly_expenses=2500.0,
    )

    assert not result.passed, "SanityChecker should have FAILED on +500k jump"
    assert result.sanity_override is True
    assert len(result.failed_rules) >= 1


def test_sanity_override_reflected_in_prediction():
    """
    EnsemblePredictor.predict must propagate sanity_override attribute
    accessible via result.metadata.sanity_override (spec requirement).
    """
    transactions = generate_series(days=180)
    predictor = _mock_ensemble_no_lstm()
    result = predictor.predict("user_sanity", transactions, horizon=30)

    # metadata is self according to domain.py
    assert hasattr(result, "sanity_override"), "sanity_override attribute missing"
    assert hasattr(result.metadata, "sanity_override"), "metadata.sanity_override missing"


# ─────────────────────────────────────────────────────────────────────────────
# Test 9 — Concurrent predictions (asyncio.gather, 50 calls)
# ─────────────────────────────────────────────────────────────────────────────

@pytest.mark.asyncio
async def test_concurrent_predictions():
    """
    50 concurrent async prediction calls must all complete without errors.
    Tests thread safety of the ensemble predictor.
    """
    predictor = _mock_ensemble_no_lstm()

    async def single_predict(i: int) -> EnsemblePrediction:
        txs = generate_series(days=120, base_balance=5000.0 + i * 10)
        return await predictor.predict_async(txs, horizon=30, account_id=f"user_{i}")

    results = await asyncio.gather(*[single_predict(i) for i in range(50)])

    assert len(results) == 50
    for i, result in enumerate(results):
        assert result is not None, f"Result {i} is None"
        assert len(result.daily_balance) == 30, f"Result {i} missing daily_balance"
        assert not any(np.isnan(d.balance) for d in result.daily_balance), (
            f"NaN in result {i}"
        )


# ─────────────────────────────────────────────────────────────────────────────
# Test 10 — Concept drift triggers retrain
# ─────────────────────────────────────────────────────────────────────────────

def test_drift_triggers_retrain():
    """
    When rolling 7-day MAE is > 2× baseline MAE, the drift detector
    must set trigger_retrain=True.
    """
    from app.monitoring.drift_detector import DriftDetector

    detector = DriftDetector()

    # Mock get_recent_actuals and get_baseline_mae
    baseline_mae = 50.0
    # Simulate 7 days of errors = 110€ each (ratio = 2.2×)
    mock_actuals = [
        {
            "actual_balance": 1000.0,
            "predicted_balance": 1110.0,
            "recorded_at": __import__("datetime").datetime.utcnow(),
        }
        for _ in range(10)
    ]

    with (
        patch("app.monitoring.drift_detector.get_recent_actuals", return_value=mock_actuals),
        patch("app.monitoring.drift_detector.get_baseline_mae", return_value=baseline_mae),
        patch("app.monitoring.drift_detector.record_prediction_actual"),
        patch("app.monitoring.drift_detector.append_quality_log"),
    ):
        result = detector.detect_concept_drift()

    assert result.trigger_retrain is True, (
        f"Expected trigger_retrain=True; drift_label={result.drift_label}, "
        f"drift_ratio={result.drift_ratio_7d}"
    )
    assert "CRITICAL" in result.drift_label.upper() or result.drift_ratio_7d >= 2.0


# ─────────────────────────────────────────────────────────────────────────────
# Sanity-check: SanityChecker passes on clean prediction
# ─────────────────────────────────────────────────────────────────────────────

def test_sanity_passes_on_clean_prediction():
    """Baseline: a smooth prediction must pass all 5 sanity rules."""
    checker = SanityChecker()
    current = 5000.0
    smooth = np.linspace(5000.0, 4800.0, 90)  # -2€/day linear decline

    result = checker.validate(
        prediction=smooth,
        current_balance=current,
        avg_daily_volume=300.0,
        avg_monthly_income=3000.0,
        avg_monthly_expenses=2500.0,
    )

    assert result.passed, f"Clean prediction should pass sanity rules: {result.failed_rules}"
    assert result.sanity_override is False
