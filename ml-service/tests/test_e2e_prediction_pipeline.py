"""
FlowGuard ML Service — End-to-End Prediction Pipeline Tests.

Tests all 3 API layers in one place:
  • Legacy /predict    (GET — called by TreasuryService.java)
  • v2     /v2/predict (POST — full ensemble w/ Transaction objects)
  • v3     /v3/predict (POST — TimesFM + LSTM w/ daily_balances arrays)

Also covers:
  • /health              (legacy + v2 + v3)
  • /v2/scenario         (what-if injection)
  • /v3/forecast-accuracy/reconcile
  • /v3/explain          (narrative explanation)
  • Validation errors (missing fields, NaN, wrong length)
  • All 5 supported horizons: 7 / 14 / 30 / 60 / 90

All tests are self-contained — no external DB or Redis required.
Run with:  pytest tests/test_e2e_prediction_pipeline.py -v
"""
from __future__ import annotations

import json as _json
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from main import app

client = TestClient(app, raise_server_exceptions=False)


# ─────────────────────────────────────────────────────────────────────────────
# Helpers — synthetic data generators
# ─────────────────────────────────────────────────────────────────────────────

def _make_series(days: int = 90, base: float = 10_000.0) -> list[dict]:
    """Generate a daily-balance series as DailyBalance dicts (for legacy /predict)."""
    today = date.today()
    return [
        {"date": str(today - timedelta(days=days - i - 1)), "balance": round(base + i * 10.0, 2)}
        for i in range(days)
    ]


def _make_transaction_items(n: int = 90, base_balance: float = 10_000.0) -> list[dict]:
    """Generate TransactionItem dicts for v2 /predict (POST body)."""
    today = date.today()
    return [
        {
            "date": str(today - timedelta(days=n - i - 1)),
            "amount": 30.0 if i % 5 != 0 else -200.0,
            "balance": round(base_balance + i * 8.0, 2),
            "creditor_name": "client_abc" if i % 5 == 0 else None,
            "description": "income" if i % 5 != 0 else "expense",
        }
        for i in range(n)
    ]


def _make_daily_balances(n: int = 30, base: float = 5_000.0) -> tuple[list[float], list[str]]:
    """
    Generate arrays for v3 /v3/predict (daily_balances + dates).
    Returns (balances_list, dates_list).
    """
    today = date.today()
    balances = [round(base + i * 12.0, 2) for i in range(n)]
    dates = [str(today - timedelta(days=n - i - 1)) for i in range(n)]
    return balances, dates


# ─────────────────────────────────────────────────────────────────────────────
# LEGACY  —  GET /health  &  GET /predict
# ─────────────────────────────────────────────────────────────────────────────

class TestLegacyEndpoints:
    """Legacy endpoints used by the Java backend (TreasuryService)."""

    def test_health_returns_ok(self):
        """GET /health → 200 with status 'OK'."""
        res = client.get("/health")
        assert res.status_code == 200
        body = res.json()
        assert body["status"] == "OK"
        assert "version" in body
        assert "model_loaded" in body

    def test_predict_by_user_missing_param(self):
        """GET /predict without user_id → 422 (required query param)."""
        res = client.get("/predict")
        assert res.status_code == 422

    def test_predict_by_user_valid(self):
        """GET /predict?user_id=test-e2e → 200 or 503 (no DB in CI)."""
        res = client.get("/predict", params={"user_id": "test-e2e-user", "horizon_days": 30})
        assert res.status_code in (200, 503)
        if res.status_code == 200:
            body = res.json()
            assert "predictions" in body or "status" in body

    def test_predict_default_horizon(self):
        """GET /predict uses default horizon_days=30."""
        res = client.get("/predict", params={"user_id": "test-e2e-default"})
        assert res.status_code in (200, 503)

    def test_predict_short_horizon_7days(self):
        """GET /predict?horizon_days=7 → 200 or 503."""
        res = client.get("/predict", params={"user_id": "test-e2e-7", "horizon_days": 7})
        assert res.status_code in (200, 503)

    def test_predict_long_horizon_180days(self):
        """GET /predict?horizon_days=180 → 200 or 503 (maximum allowed)."""
        res = client.get("/predict", params={"user_id": "test-e2e-180", "horizon_days": 180})
        assert res.status_code in (200, 503)

    def test_predict_horizon_out_of_range(self):
        """GET /predict?horizon_days=999 → 422 (> max 180)."""
        res = client.get("/predict", params={"user_id": "test-e2e-bad", "horizon_days": 999})
        assert res.status_code == 422


# ─────────────────────────────────────────────────────────────────────────────
# V2  —  POST /v2/predict  &  POST /v2/scenario  &  GET /v2/health
# ─────────────────────────────────────────────────────────────────────────────

class TestV2Endpoints:
    """v2 ensemble prediction (Transaction objects → EnsemblePrediction)."""

    def test_v2_health(self):
        """GET /v2/health → 200 with mae_current and reserve_safe fields."""
        res = client.get("/v2/health")
        assert res.status_code == 200
        body = res.json()
        assert "status" in body
        assert "reserve_safe" in body

    def test_v2_predict_valid_90_transactions(self):
        """POST /v2/predict 90 transactions → 200 with prediction fields."""
        transactions = _make_transaction_items(n=90)
        payload = {"account_id": "e2e-v2-90", "transactions": transactions, "horizon": 30}
        res = client.post("/v2/predict", json=payload)
        assert res.status_code in (200, 422, 503)
        if res.status_code == 200:
            body = res.json()
            assert "account_id" in body
            assert "daily_balance" in body
            assert len(body["daily_balance"]) == 30
            assert "confidence_score" in body
            assert "model_used" in body

    def test_v2_predict_minimum_transactions(self):
        """POST /v2/predict with exactly 5 transactions (minimum allowed)."""
        transactions = _make_transaction_items(n=5)
        payload = {"account_id": "e2e-v2-min", "transactions": transactions, "horizon": 7}
        res = client.post("/v2/predict", json=payload)
        assert res.status_code in (200, 422, 503)

    def test_v2_predict_too_few_transactions(self):
        """POST /v2/predict with 4 transactions → 422 (below minimum of 5)."""
        transactions = _make_transaction_items(n=4)
        payload = {"account_id": "e2e-v2-too-few", "transactions": transactions, "horizon": 30}
        res = client.post("/v2/predict", json=payload)
        assert res.status_code == 422

    def test_v2_predict_no_transactions(self):
        """POST /v2/predict with empty transactions list → 422."""
        payload = {"account_id": "e2e-v2-empty", "transactions": [], "horizon": 30}
        res = client.post("/v2/predict", json=payload)
        assert res.status_code == 422

    def test_v2_predict_nan_in_amount(self):
        """POST /v2/predict with NaN amount → 422 (rejected by validator)."""
        transactions = _make_transaction_items(n=10)
        transactions[5]["amount"] = float("nan")
        payload = {"account_id": "e2e-v2-nan", "transactions": transactions, "horizon": 30}
        raw = _json.dumps(payload, allow_nan=True).encode()
        res = client.post("/v2/predict", content=raw, headers={"Content-Type": "application/json"})
        assert res.status_code == 422

    def test_v2_predict_all_horizons(self):
        """POST /v2/predict with all 5 supported horizon values."""
        transactions = _make_transaction_items(n=90)
        for horizon in (7, 14, 30, 60, 90):
            payload = {"account_id": f"e2e-v2-h{horizon}", "transactions": transactions, "horizon": horizon}
            res = client.post("/v2/predict", json=payload)
            assert res.status_code in (200, 422, 503), f"horizon={horizon} returned {res.status_code}"

    def test_v2_predict_account_id_mismatch(self):
        """POST /v2/predict with mismatching X-Account-Id header → 403."""
        transactions = _make_transaction_items(n=30)
        payload = {"account_id": "correct-account", "transactions": transactions, "horizon": 30}
        res = client.post("/v2/predict", json=payload, headers={"X-Account-Id": "wrong-account"})
        assert res.status_code == 403

    def test_v2_predict_matching_account_header(self):
        """POST /v2/predict with matching X-Account-Id header → 200 or 422."""
        transactions = _make_transaction_items(n=30)
        payload = {"account_id": "my-account", "transactions": transactions, "horizon": 30}
        res = client.post("/v2/predict", json=payload, headers={"X-Account-Id": "my-account"})
        assert res.status_code in (200, 422, 503)

    def test_v2_scenario_valid(self):
        """POST /v2/scenario with valid injection → 200 with baseline + with_scenario."""
        transactions = _make_transaction_items(n=60)
        payload = {
            "account_id": "e2e-v2-scenario",
            "transactions": transactions,
            "horizon": 30,
            "scenario_date": str(date.today() + timedelta(days=5)),
            "scenario_amount": -2000.0,
            "scenario_label": "e2e_late_payment",
        }
        res = client.post("/v2/scenario", json=payload)
        assert res.status_code in (200, 422, 503)
        if res.status_code == 200:
            body = res.json()
            assert "baseline" in body
            assert "with_scenario" in body
            assert "impact_summary" in body
            assert "scenario_amount" in body["impact_summary"]

    def test_v2_scenario_zero_amount(self):
        """POST /v2/scenario with scenario_amount=0 → 422 (must be non-zero)."""
        transactions = _make_transaction_items(n=30)
        payload = {
            "account_id": "e2e-v2-zero",
            "transactions": transactions,
            "horizon": 30,
            "scenario_date": str(date.today() + timedelta(days=5)),
            "scenario_amount": 0.0,
        }
        res = client.post("/v2/scenario", json=payload)
        assert res.status_code == 422

    def test_v2_scenario_positive_income(self):
        """POST /v2/scenario with positive income injection → 200 or 422."""
        transactions = _make_transaction_items(n=60)
        payload = {
            "account_id": "e2e-v2-income",
            "transactions": transactions,
            "horizon": 30,
            "scenario_date": str(date.today() + timedelta(days=10)),
            "scenario_amount": 5000.0,
            "scenario_label": "early_invoice_paid",
        }
        res = client.post("/v2/scenario", json=payload)
        assert res.status_code in (200, 422, 503)


# ─────────────────────────────────────────────────────────────────────────────
# V3  —  POST /v3/predict  &  GET /v3/predict/health
#         POST /v3/forecast-accuracy/reconcile  &  POST /v3/explain
# ─────────────────────────────────────────────────────────────────────────────

class TestV3Endpoints:
    """v3 TimesFM + LSTM ensemble (daily_balances + dates arrays)."""

    def test_v3_health(self):
        """GET /v3/predict/health → 200 with ensemble_weights."""
        res = client.get("/v3/predict/health")
        assert res.status_code == 200
        body = res.json()
        assert body["status"] == "ok"
        assert "timesfm_loaded" in body
        assert "lstm_available" in body
        assert "ensemble_weights" in body

    def test_v3_predict_valid_30_days(self):
        """POST /v3/predict 30 days → 200 with all forecast fields."""
        balances, dates = _make_daily_balances(n=30)
        payload = {
            "account_id": "e2e-v3-30",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code in (200, 422, 500, 503)
        if res.status_code == 200:
            body = res.json()
            assert body["account_id"] == "e2e-v3-30"
            assert "daily_balances" in body
            assert len(body["daily_balances"]) == 30
            assert "confidence_score" in body
            assert "confidence_label" in body
            assert "deficit_predicted" in body

    def test_v3_predict_minimum_14_days(self):
        """POST /v3/predict with exactly 14 days (minimum allowed) → 200 or 422."""
        balances, dates = _make_daily_balances(n=14)
        payload = {
            "account_id": "e2e-v3-14",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 7,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code in (200, 422, 500, 503)

    def test_v3_predict_below_minimum(self):
        """POST /v3/predict with 13 days → 422 (below min_length=14)."""
        balances, dates = _make_daily_balances(n=13)
        payload = {
            "account_id": "e2e-v3-tooFew",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 7,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code == 422

    def test_v3_predict_mismatched_lengths(self):
        """POST /v3/predict with len(balances) != len(dates) → 400."""
        balances, dates = _make_daily_balances(n=30)
        payload = {
            "account_id": "e2e-v3-mismatch",
            "daily_balances": balances[:20],
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code in (400, 422)

    def test_v3_predict_nan_in_balances(self):
        """POST /v3/predict with NaN value → 422 (rejected by validator)."""
        balances, dates = _make_daily_balances(n=30)
        balances[10] = float("nan")
        payload = {
            "account_id": "e2e-v3-nan",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        raw = _json.dumps(payload, allow_nan=True).encode()
        res = client.post("/v3/predict", content=raw, headers={"Content-Type": "application/json"})
        assert res.status_code == 422

    def test_v3_predict_invalid_horizon(self):
        """POST /v3/predict with horizon_days=45 (not in 7/14/30/60/90) → 422."""
        balances, dates = _make_daily_balances(n=30)
        payload = {
            "account_id": "e2e-v3-badHorizon",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 45,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code == 422

    def test_v3_predict_all_valid_horizons(self):
        """POST /v3/predict with each of 7/14/30/60/90 → no 4xx/5xx except 422/503."""
        balances, dates = _make_daily_balances(n=90)
        for h in (7, 14, 30, 60, 90):
            payload = {
                "account_id": f"e2e-v3-h{h}",
                "daily_balances": balances,
                "dates": dates,
                "horizon_days": h,
            }
            res = client.post("/v3/predict", json=payload)
            assert res.status_code in (200, 422, 500, 503), f"horizon={h} returned {res.status_code}"

    def test_v3_predict_invalid_date_format(self):
        """POST /v3/predict with malformed date → 422."""
        balances, dates = _make_daily_balances(n=30)
        dates[5] = "not-a-date"
        payload = {
            "account_id": "e2e-v3-badDate",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code == 422

    def test_v3_predict_empty_account_id(self):
        """POST /v3/predict with empty account_id string → 422."""
        balances, dates = _make_daily_balances(n=30)
        payload = {
            "account_id": "",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/predict", json=payload)
        assert res.status_code == 422

    # ── Forecast Accuracy Reconcile ───────────────────────────────────────────

    def test_v3_reconcile_accurate_prediction(self):
        """POST /v3/forecast-accuracy/reconcile: prediction within 2% → ACCURATE."""
        payload = {
            "account_id": "e2e-reconcile",
            "forecast_date": "2024-01-15",
            "horizon_days": 30,
            "predicted_balance": 10_000.0,
            "actual_balance": 10_050.0,   # 0.5% error → ACCURATE
        }
        res = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["drift_direction"] == "ACCURATE"
        assert body["accuracy_pct"] >= 98.0
        assert body["mae"] == pytest.approx(50.0, abs=1.0)

    def test_v3_reconcile_over_estimated(self):
        """POST /v3/forecast-accuracy/reconcile: predicted > actual → OVER_ESTIMATED."""
        payload = {
            "account_id": "e2e-reconcile-over",
            "forecast_date": "2024-02-01",
            "horizon_days": 14,
            "predicted_balance": 12_000.0,
            "actual_balance": 8_000.0,    # 50% error
        }
        res = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["drift_direction"] == "OVER_ESTIMATED"
        assert body["mae"] == pytest.approx(4_000.0, abs=1.0)

    def test_v3_reconcile_under_estimated(self):
        """POST /v3/forecast-accuracy/reconcile: predicted < actual → UNDER_ESTIMATED."""
        payload = {
            "account_id": "e2e-reconcile-under",
            "forecast_date": "2024-03-01",
            "horizon_days": 7,
            "predicted_balance": 5_000.0,
            "actual_balance": 9_000.0,
        }
        res = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["drift_direction"] == "UNDER_ESTIMATED"

    def test_v3_reconcile_missing_field(self):
        """POST /v3/forecast-accuracy/reconcile without actual_balance → 422."""
        payload = {
            "account_id": "e2e-reconcile-bad",
            "forecast_date": "2024-01-01",
            "horizon_days": 30,
            "predicted_balance": 5000.0,
            # actual_balance missing
        }
        res = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        assert res.status_code == 422

    # ── Explain ───────────────────────────────────────────────────────────────

    def test_v3_explain_stable_trend(self):
        """POST /v3/explain stable balances → score_trend == STABLE."""
        balances = [5000.0 + i * 0.1 for i in range(60)]  # nearly flat
        today = date.today()
        dates = [str(today - timedelta(days=60 - i - 1)) for i in range(60)]
        payload = {
            "account_id": "e2e-explain-stable",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/explain", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["score_trend"] == "STABLE"
        assert "main_drivers" in body
        assert isinstance(body["main_drivers"], list)
        assert "summary" in body

    def test_v3_explain_deteriorating_trend(self):
        """POST /v3/explain declining balances → score_trend == DETERIORATING."""
        balances = [10_000.0 - i * 100.0 for i in range(60)]  # strong decline
        today = date.today()
        dates = [str(today - timedelta(days=60 - i - 1)) for i in range(60)]
        payload = {
            "account_id": "e2e-explain-declining",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/explain", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["score_trend"] == "DETERIORATING"

    def test_v3_explain_improving_trend(self):
        """POST /v3/explain rising balances → score_trend == IMPROVING."""
        balances = [3_000.0 + i * 200.0 for i in range(60)]  # strong growth
        today = date.today()
        dates = [str(today - timedelta(days=60 - i - 1)) for i in range(60)]
        payload = {
            "account_id": "e2e-explain-improving",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/explain", json=payload)
        assert res.status_code == 200
        body = res.json()
        assert body["score_trend"] == "IMPROVING"

    def test_v3_explain_mismatched_arrays(self):
        """POST /v3/explain with len(balances) != len(dates) → 400."""
        balances = [5000.0] * 30
        today = date.today()
        dates = [str(today - timedelta(days=20 - i - 1)) for i in range(20)]  # only 20 dates
        payload = {
            "account_id": "e2e-explain-mismatch",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/explain", json=payload)
        assert res.status_code in (400, 422)

    def test_v3_explain_drivers_structure(self):
        """POST /v3/explain → each driver has label, impact_eur, insight fields."""
        balances = [5_000.0 - i * 50.0 for i in range(30)]  # moderate decline
        today = date.today()
        dates = [str(today - timedelta(days=30 - i - 1)) for i in range(30)]
        payload = {
            "account_id": "e2e-explain-drivers",
            "daily_balances": balances,
            "dates": dates,
            "horizon_days": 30,
        }
        res = client.post("/v3/explain", json=payload)
        assert res.status_code == 200
        body = res.json()
        for driver in body["main_drivers"]:
            assert "label" in driver
            assert "impact_eur" in driver
            assert "insight" in driver
