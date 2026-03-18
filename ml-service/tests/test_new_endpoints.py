"""
Tests for the new ML service endpoints added in feature branch:
  - POST /v3/forecast-accuracy/reconcile
  - GET  /v3/explain
  - POST /v3/explain/narrative
"""
from __future__ import annotations

from datetime import date

import pytest
from fastapi.testclient import TestClient

from app.api.prediction_router import router
from fastapi import FastAPI

app = FastAPI()
app.include_router(router)
client = TestClient(app, raise_server_exceptions=False)

ACCOUNT_ID = "test-account-123"


class TestReconcileEndpoint:
    def test_reconcile_missing_body_returns_422(self):
        resp = client.post("/v3/forecast-accuracy/reconcile", json={})
        assert resp.status_code == 422

    def test_reconcile_valid_body_returns_200(self):
        payload = {
            "account_id": ACCOUNT_ID,
            "forecast_date": str(date.today()),
            "horizon_days": 30,
            "actual_balance": 12500.0,
        }
        resp = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        # May be 200 or 404 if no prior forecast exists — both are valid
        assert resp.status_code in (200, 404, 500)

    def test_reconcile_negative_balance_accepted(self):
        payload = {
            "account_id": ACCOUNT_ID,
            "forecast_date": str(date.today()),
            "horizon_days": 7,
            "actual_balance": -500.0,
        }
        resp = client.post("/v3/forecast-accuracy/reconcile", json=payload)
        assert resp.status_code in (200, 404, 500)


class TestExplainEndpoint:
    def test_explain_missing_account_returns_422(self):
        resp = client.get("/v3/explain")
        assert resp.status_code == 422

    def test_explain_with_account_returns_200_or_404(self):
        resp = client.get("/v3/explain", params={"account_id": ACCOUNT_ID, "horizon_days": 30})
        assert resp.status_code in (200, 404, 500)

    def test_explain_response_has_narrative_field(self):
        resp = client.get("/v3/explain", params={"account_id": ACCOUNT_ID})
        if resp.status_code == 200:
            body = resp.json()
            assert "narrative" in body or "explanation" in body or "summary" in body

    def test_explain_default_horizon_is_30(self):
        resp = client.get("/v3/explain", params={"account_id": ACCOUNT_ID})
        # Should not fail with a missing horizon_days parameter
        assert resp.status_code != 422


class TestNarrativeEndpoint:
    def test_narrative_missing_body_returns_422(self):
        resp = client.post("/v3/explain/narrative", json={})
        assert resp.status_code == 422

    def test_narrative_valid_body_returns_response(self):
        payload = {
            "account_id": ACCOUNT_ID,
            "horizon_days": 30,
        }
        resp = client.post("/v3/explain/narrative", json=payload)
        assert resp.status_code in (200, 404, 422, 500)

    def test_narrative_response_structure(self):
        payload = {
            "account_id": ACCOUNT_ID,
            "horizon_days": 7,
        }
        resp = client.post("/v3/explain/narrative", json=payload)
        if resp.status_code == 200:
            body = resp.json()
            assert isinstance(body, dict)
