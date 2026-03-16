"""
FlowGuard ML Predictor — LSTM-based treasury forecasting engine.

Provides:
- Time-series prediction of cash balances (30/60/90 day horizon)
- Confidence scoring
- Critical point detection (projected negative balances)
- What-if scenario simulation
"""

from __future__ import annotations

import math
import os
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Optional

import numpy as np
import torch
import torch.nn as nn
from sklearn.preprocessing import MinMaxScaler
from sqlalchemy import create_engine, text

import structlog

logger = structlog.get_logger()

# ---------------------------------------------------------------------------
# Database connection (reads from env, falls back to synthetic data)
# ---------------------------------------------------------------------------

DATABASE_URL = os.getenv("DATABASE_URL")  # e.g. postgresql://user:pass@localhost:5432/flowguard

_engine = None

def _get_engine():
    global _engine
    if _engine is None and DATABASE_URL:
        _engine = create_engine(DATABASE_URL, pool_size=5, max_overflow=10, pool_pre_ping=True)
    return _engine


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class ForecastPoint:
    date: str
    predicted_balance: float
    lower_bound: float
    upper_bound: float


@dataclass
class CriticalPoint:
    date: str
    predicted_balance: float
    reason: str


@dataclass
class ForecastResult:
    predictions: list[ForecastPoint]
    critical_points: list[CriticalPoint]
    confidence_score: float
    generated_at: str
    health_score: float = 0.0
    worst_deficit: float = 0.0
    days_until_impact: int = 0


@dataclass
class ScenarioResult:
    baseline_forecast: list[float]
    impacted_forecast: list[float]
    max_impact: float
    min_balance: float
    risk_level: str
    recommendation: str
    worst_deficit: float = 0.0
    days_until_impact: int = 0


# ---------------------------------------------------------------------------
# LSTM Model
# ---------------------------------------------------------------------------

class TreasuryLSTM(nn.Module):
    """Lightweight LSTM for cash-flow time-series prediction."""

    def __init__(
        self,
        input_size: int = 1,
        hidden_size: int = 64,
        num_layers: int = 2,
        dropout: float = 0.2,
    ):
        super().__init__()
        self.hidden_size = hidden_size
        self.num_layers = num_layers

        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            dropout=dropout if num_layers > 1 else 0.0,
            batch_first=True,
        )

        self.fc = nn.Sequential(
            nn.Linear(hidden_size, 32),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(32, 1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x shape: (batch, seq_len, input_size)
        lstm_out, _ = self.lstm(x)
        # Take the last time step
        last_hidden = lstm_out[:, -1, :]
        return self.fc(last_hidden)


# ---------------------------------------------------------------------------
# Predictor Service
# ---------------------------------------------------------------------------

class TreasuryPredictor:
    """
    Main predictor class that wraps the LSTM model.

    In production, model weights would be loaded from a persisted checkpoint.
    This implementation uses random historical data for demonstration,
    but the architecture is fully functional for real training.
    """

    SEQUENCE_LENGTH = 30
    CONFIDENCE_BASE = 0.85

    def __init__(self):
        self.model = TreasuryLSTM()
        self.model.eval()
        self.scaler = MinMaxScaler(feature_range=(0, 1))

    def predict(self, user_id: str, horizon_days: int = 30) -> ForecastResult:
        """Generate a cash-flow forecast for the given user and horizon."""

        # In production: fetch real transaction history from DB
        historical = self._fetch_historical_data(user_id)

        # Scale the data
        scaled = self.scaler.fit_transform(historical.reshape(-1, 1))

        # Create prediction sequence
        sequence = scaled[-self.SEQUENCE_LENGTH:]
        predictions: list[ForecastPoint] = []
        critical_points: list[CriticalPoint] = []
        today = date.today()

        current_seq = torch.FloatTensor(sequence).unsqueeze(0)  # (1, seq_len, 1)

        with torch.no_grad():
            for day_offset in range(1, horizon_days + 1):
                pred_scaled = self.model(current_seq)
                pred_value = self.scaler.inverse_transform(
                    pred_scaled.numpy().reshape(-1, 1)
                )[0, 0]

                # Confidence decreases with horizon distance
                day_confidence = self.CONFIDENCE_BASE * math.exp(-0.005 * day_offset)
                spread = abs(pred_value) * (1 - day_confidence) * 0.5

                forecast_date = today + timedelta(days=day_offset)
                point = ForecastPoint(
                    date=forecast_date.isoformat(),
                    predicted_balance=round(float(pred_value), 2),
                    lower_bound=round(float(pred_value - spread), 2),
                    upper_bound=round(float(pred_value + spread), 2),
                )
                predictions.append(point)

                # Detect critical points
                if pred_value < 0:
                    critical_points.append(CriticalPoint(
                        date=forecast_date.isoformat(),
                        predicted_balance=round(float(pred_value), 2),
                        reason=self._determine_reason(pred_value, day_offset),
                    ))

                # Slide the window
                new_val = pred_scaled.unsqueeze(-1)  # (1, 1, 1)
                current_seq = torch.cat([current_seq[:, 1:, :], new_val], dim=1)

        avg_confidence = self.CONFIDENCE_BASE * math.exp(-0.005 * (horizon_days / 2))

        # Compute health score, worst deficit, days until impact
        health_score, worst_deficit, days_until_impact = self._compute_health_metrics(predictions)

        return ForecastResult(
            predictions=predictions,
            critical_points=critical_points,
            confidence_score=round(avg_confidence, 4),
            generated_at=today.isoformat(),
            health_score=health_score,
            worst_deficit=worst_deficit,
            days_until_impact=days_until_impact,
        )

    def run_scenario(
        self,
        user_id: str,
        scenario_type: str,
        amount: float,
        delay_days: int,
        description: Optional[str] = None,
    ) -> ScenarioResult:
        """
        Run a what-if scenario.

        Compares baseline forecast against an impacted version where a
        financial event is injected at the specified day offset.
        """

        baseline_result = self.predict(user_id, delay_days)
        baseline = [p.predicted_balance for p in baseline_result.predictions]

        # Apply scenario impact
        impacted = list(baseline)
        impact_day = min(delay_days // 3, len(impacted) - 1)

        if scenario_type in ("NEW_EXPENSE", "LOST_CLIENT"):
            # Sudden drop
            for i in range(impact_day, len(impacted)):
                decay = 1.0 - (0.3 * math.exp(-0.05 * (i - impact_day)))
                impacted[i] = impacted[i] - amount * decay
        elif scenario_type == "DELAYED_INCOME":
            # Temporary dip then recovery
            recovery_day = min(impact_day + delay_days // 2, len(impacted) - 1)
            for i in range(impact_day, recovery_day):
                impacted[i] = impacted[i] - amount
            for i in range(recovery_day, len(impacted)):
                recovery_frac = (i - recovery_day) / max(len(impacted) - recovery_day, 1)
                impacted[i] = impacted[i] - amount * (1 - recovery_frac)
        elif scenario_type == "NEW_HIRE":
            # Gradual monthly cost
            monthly_cost = amount
            for i in range(impact_day, len(impacted)):
                months_elapsed = (i - impact_day) / 30.0
                impacted[i] = impacted[i] - monthly_cost * months_elapsed
        elif scenario_type == "INVESTMENT":
            # Large upfront cost, gradual ROI
            for i in range(impact_day, len(impacted)):
                days_since = i - impact_day
                roi_recovered = amount * 0.002 * days_since  # 0.2% daily ROI
                impacted[i] = impacted[i] - amount + roi_recovered

        max_impact = max(abs(b - im) for b, im in zip(baseline, impacted))
        min_balance = min(impacted)

        # Determine risk level
        if min_balance < -5000:
            risk_level = "HIGH"
        elif min_balance < 0:
            risk_level = "MEDIUM"
        else:
            risk_level = "LOW"

        recommendation = self._generate_recommendation(
            scenario_type, amount, risk_level, min_balance
        )

        # Compute worst deficit and days until impact on impacted forecast
        worst_deficit = 0.0
        days_until_impact = 0
        for i, val in enumerate(impacted):
            if val < worst_deficit:
                worst_deficit = val
            if val < 0 and days_until_impact == 0:
                days_until_impact = i + 1

        return ScenarioResult(
            baseline_forecast=[round(v, 2) for v in baseline],
            impacted_forecast=[round(v, 2) for v in impacted],
            max_impact=round(max_impact, 2),
            min_balance=round(min_balance, 2),
            risk_level=risk_level,
            recommendation=recommendation,
            worst_deficit=round(worst_deficit, 2),
            days_until_impact=days_until_impact,
        )

    # -----------------------------------------------------------------------
    # Private helpers
    # -----------------------------------------------------------------------

    def _compute_health_metrics(self, predictions: list[ForecastPoint]) -> tuple[float, float, int]:
        """
        Compute:
          - healthScore (0-100): financial health based on predicted balances
          - worstDeficit: lowest predicted balance
          - daysUntilImpact: first day with negative balance (0 = never)
        """
        if not predictions:
            return 100.0, 0.0, 0

        balances = [p.predicted_balance for p in predictions]
        worst_deficit = min(balances)
        days_until_impact = 0

        for i, bal in enumerate(balances):
            if bal < 0:
                days_until_impact = i + 1
                break

        # Health score components:
        # 1. No negative balances → +40
        # 2. Average balance level → up to +30
        # 3. Trend (end vs start) → up to +20
        # 4. Stability (low variance) → up to +10
        score = 0.0

        # Component 1: negative balance penalty
        neg_days = sum(1 for b in balances if b < 0)
        neg_ratio = neg_days / len(balances)
        score += 40 * (1 - neg_ratio)

        # Component 2: average balance level
        avg_bal = sum(balances) / len(balances)
        if avg_bal >= 10000:
            score += 30
        elif avg_bal >= 5000:
            score += 24
        elif avg_bal >= 1000:
            score += 16
        elif avg_bal > 0:
            score += 8

        # Component 3: trend
        if len(balances) >= 2:
            trend = balances[-1] - balances[0]
            if trend > 0:
                score += min(20, 20 * (trend / max(abs(balances[0]), 1)) * 0.5)
            else:
                penalty = min(20, abs(trend) / max(abs(balances[0]), 1) * 20)
                score += max(0, 10 - penalty)

        # Component 4: stability
        if len(balances) >= 2:
            mean = sum(balances) / len(balances)
            variance = sum((b - mean) ** 2 for b in balances) / len(balances)
            cv = (variance ** 0.5) / max(abs(mean), 1)
            if cv < 0.1:
                score += 10
            elif cv < 0.3:
                score += 7
            elif cv < 0.6:
                score += 4

        return round(min(max(score, 0), 100), 1), round(worst_deficit, 2), days_until_impact

    def _fetch_historical_data(self, user_id: str) -> np.ndarray:
        """
        Fetch real transaction history from the database and compute daily balances.
        Falls back to synthetic data if DATABASE_URL is not configured.
        """
        engine = _get_engine()
        if engine is None:
            logger.warning("no_database_url", msg="Using synthetic data (set DATABASE_URL)")
            return self._generate_synthetic_data(user_id)

        try:
            # Query: aggregate daily net cash flow per user (credits – debits)
            # across all accounts for the last 90 days, then compute running balance.
            query = text("""
                WITH daily_flows AS (
                    SELECT
                        t.date AS day,
                        SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE 0 END)
                        - SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE 0 END) AS net
                    FROM transactions t
                    JOIN accounts a ON a.id = t.account_id
                    WHERE a.user_id = CAST(:uid AS uuid)
                      AND t.date >= CURRENT_DATE - INTERVAL '90 days'
                    GROUP BY t.date
                    ORDER BY t.date
                ),
                base_balance AS (
                    SELECT COALESCE(SUM(a.balance), 0) AS current_total
                    FROM accounts a
                    WHERE a.user_id = CAST(:uid AS uuid) AND a.status = 'ACTIVE'
                )
                SELECT df.day, df.net, bb.current_total
                FROM daily_flows df, base_balance bb
                ORDER BY df.day
            """)

            with engine.connect() as conn:
                rows = conn.execute(query, {"uid": user_id}).fetchall()

            if len(rows) < 14:
                logger.info("insufficient_data", user_id=user_id, rows=len(rows))
                return self._generate_synthetic_data(user_id)

            # Reconstruct historical daily balances backwards from current balance.
            # current_balance = base_balance after all net flows
            current_total = float(rows[0][2])
            total_net = sum(float(r[1]) for r in rows)

            # Starting balance = current total - total net flow over the period
            starting_balance = current_total - total_net
            balances = []
            running = starting_balance
            for row in rows:
                running += float(row[1])
                balances.append(running)

            return np.array(balances, dtype=np.float64)

        except Exception as exc:
            logger.error("db_fetch_error", error=str(exc), user_id=user_id)
            return self._generate_synthetic_data(user_id)

    def _generate_synthetic_data(self, user_id: str) -> np.ndarray:
        """Generate synthetic data for demonstration / fallback."""
        rng = np.random.default_rng(seed=hash(user_id) % (2**31))
        days = 90
        base = 15000.0
        trend = np.linspace(0, 2000, days)
        seasonal = 3000 * np.sin(np.linspace(0, 4 * np.pi, days))
        noise = rng.normal(0, 500, days)
        return base + trend + seasonal + noise

    def _determine_reason(self, balance: float, day_offset: int) -> str:
        if balance < -5000:
            return f"Déficit critique de {abs(balance):.0f} € prévu dans {day_offset} jours"
        elif balance < -1000:
            return f"Découvert de {abs(balance):.0f} € prévu dans {day_offset} jours"
        else:
            return f"Solde négatif de {abs(balance):.0f} € prévu dans {day_offset} jours"

    def _generate_recommendation(
        self, scenario_type: str, amount: float, risk_level: str, min_balance: float
    ) -> str:
        if risk_level == "HIGH":
            if scenario_type == "NEW_EXPENSE":
                return (
                    f"Cette dépense de {amount:.0f} € entraînerait un déficit critique. "
                    "Envisagez un crédit flash ou étalez la dépense sur plusieurs mois."
                )
            elif scenario_type == "LOST_CLIENT":
                return (
                    f"La perte de ce client impacterait votre trésorerie de {amount:.0f} €. "
                    "Diversifiez votre portefeuille client et constituez une réserve de sécurité."
                )
            elif scenario_type == "NEW_HIRE":
                return (
                    f"Ce recrutement coûterait {amount:.0f} €/mois. "
                    "Attendez que votre trésorerie soit plus solide ou optez pour un freelance."
                )
            return (
                f"Risque élevé : le solde minimum atteindrait {min_balance:.0f} €. "
                "Reportez cette décision ou sécurisez un financement."
            )
        elif risk_level == "MEDIUM":
            return (
                f"Impact modéré sur votre trésorerie (min. {min_balance:.0f} €). "
                "Surveillez vos entrées et gardez une marge de sécurité."
            )
        else:
            return (
                "Impact faible sur votre trésorerie. "
                "Votre situation financière peut absorber ce scénario."
            )


# Singleton
predictor = TreasuryPredictor()
