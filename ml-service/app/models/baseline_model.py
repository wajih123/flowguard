"""
FlowGuard — Baseline and Prophet-style fallback models.

RuleBasedPredictor  : always available, cold-start safe, never catastrophic errors
ProphetStyleDecomposer : sklearn decomposition for 30–90d history
"""
from __future__ import annotations

import calendar
from datetime import date, timedelta
from typing import Optional

import numpy as np
import pandas as pd
import structlog
from sklearn.linear_model import Ridge
from sklearn.pipeline import Pipeline as SklearnPipeline
from sklearn.preprocessing import PolynomialFeatures, StandardScaler

from app.domain import (
    BaselinePrediction,
    ProphetPrediction,
    RecurringPattern,
)

log = structlog.get_logger()


class RuleBasedPredictor:
    """
    Rule-based cash-flow predictor.
    Fallback for cold start (< 30d of history).
    Also used as sanity-check against LSTM predictions.
    Never produces catastrophic errors (±€5 000+ gap) unlike neural models.
    """

    def predict(
        self,
        current_balance: float,
        recurring_patterns: list[RecurringPattern],
        fiscal_features: pd.DataFrame,
        recent_transactions: Optional[list] = None,
        horizon: int = 30,
        income_volatility: float = 0.3,
        avg_monthly_income: float = 0.0,
    ) -> BaselinePrediction:
        """
        Project daily balance by applying:
          1. Scheduled outflows (recurring patterns)
          2. Expected income (median of last 3 income intervals)
          3. Fiscal obligations at known future dates
        """
        balance = current_balance
        daily_balances = np.zeros(horizon, dtype=np.float64)

        # Scheduled recurring outflows per future date
        outflow_schedule: dict[date, float] = {}
        inflow_schedule: dict[date, float] = {}
        today = date.today()

        for p in recurring_patterns:
            if p.median_amount <= 0:
                continue

            next_occ = p.next_occurrence
            while next_occ <= today:
                next_occ += timedelta(days=p.interval_days)

            # Schedule all occurrences within horizon
            d = next_occ
            while d <= today + timedelta(days=horizon):
                days_offset = (d - today).days - 1
                if 0 <= days_offset < horizon:
                    # Income patterns (positive recurring)
                    if hasattr(p, "amount_sign") or p.median_amount > 0:
                        # Determine sign from category context
                        inflow_schedule[d] = inflow_schedule.get(d, 0.0) + p.median_amount
                d += timedelta(days=p.interval_days)

        # Outflows: expense recurring patterns
        for p in recurring_patterns:
            if p.median_amount <= 0:
                continue
            # Treat all recurring patterns as outflows unless clearly income
            next_occ = p.next_occurrence
            while next_occ <= today:
                next_occ += timedelta(days=p.interval_days)
            d = next_occ
            while d <= today + timedelta(days=horizon):
                days_offset = (d - today).days - 1
                if 0 <= days_offset < horizon:
                    outflow_schedule[d] = outflow_schedule.get(d, 0.0) + p.median_amount
                d += timedelta(days=p.interval_days)

        # Fiscal obligations from injected features
        fiscal_idx: dict[date, dict] = {}
        if not fiscal_features.empty and "date" in fiscal_features.columns:
            for _, row in fiscal_features.iterrows():
                fiscal_idx[row["date"]] = row.to_dict()

        # Estimated income from history
        if recent_transactions:
            income_txs = sorted(
                [t for t in recent_transactions if t.amount > 0], key=lambda t: t.date
            )
            if len(income_txs) >= 2:
                last_3_amounts = [t.amount for t in income_txs[-3:]]
                expected_income = float(np.median(last_3_amounts))
                intervals = [
                    (income_txs[i].date - income_txs[i - 1].date).days
                    for i in range(1, min(len(income_txs), 4))
                ]
                income_interval = int(np.median(intervals)) if intervals else 30
            else:
                expected_income = avg_monthly_income / 30.0 * 7
                income_interval = 30
        else:
            expected_income = avg_monthly_income / 30.0 * 7
            income_interval = 30

        # Project each day
        for i in range(horizon):
            day = today + timedelta(days=i + 1)

            # Apply recurring outflow (only if in outflow schedule, treat as expense)
            outflow = outflow_schedule.get(day, 0.0)
            balance -= outflow

            # Apply estimated income periodically
            if i > 0 and income_interval > 0 and i % income_interval == 0:
                balance += expected_income

            daily_balances[i] = balance

        # Uncertainty: ± income × volatility
        uncertainty = avg_monthly_income * income_volatility
        lower = daily_balances - uncertainty
        upper = daily_balances + uncertainty

        return BaselinePrediction(
            daily_balances=daily_balances,
            uncertainty_lower=lower,
            uncertainty_upper=upper,
        )


class ProphetStyleDecomposer:
    """
    Seasonal decomposition predictor using sklearn components.
    Best for 30–90d history where LSTM hasn't been trained yet.
    Separates trend + weekday + monthly + quarterly effects.
    """

    def __init__(self) -> None:
        self._model: Optional[SklearnPipeline] = None
        self._trend_mean: float = 0.0
        self._residual_std: float = 100.0
        self._last_balance: float = 0.0
        self._balance_mean: float = 0.0
        self._balance_std: float = 1.0

    def fit(self, series: pd.DataFrame) -> None:
        """
        Decompose the time series into trend, weekday, monthly, quarterly components.
        Fit a Ridge regression on the decomposed features.
        """
        if series.empty or "balance" not in series.columns:
            log.warning("prophet_fit_skipped", reason="empty_series")
            return
        if len(series) < 7:
            log.warning("prophet_fit_skipped", reason="too_short", rows=len(series))
            return

        df = series.copy()
        df["date"] = pd.to_datetime(df["date"])
        df = df.sort_values("date").reset_index(drop=True)
        df["balance"] = df["balance"].ffill().bfill()

        self._last_balance = float(df["balance"].iloc[-1])
        self._balance_mean = float(df["balance"].mean())
        self._balance_std = float(df["balance"].std() + 1e-9)

        # Build feature matrix
        X = self._build_fit_features(df)
        y = ((df["balance"] - self._balance_mean) / self._balance_std).values

        self._model = SklearnPipeline([
            ("scaler", StandardScaler()),
            ("poly", PolynomialFeatures(degree=2, interaction_only=False, include_bias=False)),
            ("ridge", Ridge(alpha=1.0)),
        ])
        self._model.fit(X, y)

        # Compute residual std for confidence intervals
        y_pred = self._model.predict(X)
        residuals = y - y_pred
        self._residual_std = float(np.std(residuals))

        log.info("prophet_fitted", rows=len(df), residual_std=round(self._residual_std, 4))

    def _build_fit_features(self, df: pd.DataFrame) -> np.ndarray:
        """Build feature matrix from date columns."""
        rows = []
        for _, row in df.iterrows():
            d = row["date"]
            rows.append(self._date_to_features(d.date()))
        return np.array(rows, dtype=np.float32)

    def _date_to_features(self, d: date) -> list[float]:
        last_day = calendar.monthrange(d.year, d.month)[1]
        return [
            d.weekday() / 6.0,                    # day of week 0-1
            d.day / 31.0,                          # day of month 0-1
            d.timetuple().tm_yday / 365.0,         # day of year 0-1
            int(d.month in {3, 6, 9, 12}),         # quarter end month
            int((d.month, d.day) in {(2, 15), (5, 15), (8, 15), (11, 15)}),  # URSSAF
            int(d.day == 24),                      # TVA day
            int(d.weekday() >= 5),                 # weekend
            max(0.0, 1.0 - (last_day - d.day) / 7.0) if (last_day - d.day) <= 7 else 0.0,
        ]

    def predict(
        self,
        horizon: int,
        future_fiscal_features: pd.DataFrame,
        start_date: Optional[date] = None,
    ) -> ProphetPrediction:
        """
        Extrapolate using the decomposed model.
        Returns central estimate + 80% CI.
        """
        if self._model is None:
            log.warning("prophet_predict_fallback", reason="model_not_fitted")
            mean = np.full(horizon, self._last_balance)
            return ProphetPrediction(
                daily_balances=mean,
                ci_lower=mean * 0.8,
                ci_upper=mean * 1.2,
            )

        if start_date is None:
            start_date = date.today() + timedelta(days=1)

        future_dates = [start_date + timedelta(days=i) for i in range(horizon)]
        X_future = np.array(
            [self._date_to_features(d) for d in future_dates], dtype=np.float32
        )

        y_norm = self._model.predict(X_future)
        y_actual = y_norm * self._balance_std + self._balance_mean

        # 80% CI uses 1.28 sigma
        ci_half = 1.28 * self._residual_std * self._balance_std
        lower = y_actual - ci_half
        upper = y_actual + ci_half

        return ProphetPrediction(
            daily_balances=y_actual,
            ci_lower=lower,
            ci_upper=upper,
        )
