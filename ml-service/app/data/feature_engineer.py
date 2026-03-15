"""
FlowGuard — FreelanceFeatureEngineer
Construction des features spécifiques aux freelances français pour le LSTM.
Ces features ne sont PAS disponibles dans TimesFM (modèle générique).
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from datetime import datetime


class FreelanceFeatureEngineer:
    """
    Features spécifiques aux patterns de trésorerie des freelances français.
    Ce sont ces features qui différencient le LSTM FlowGuard de TimesFM.
    """

    def build_features(
        self,
        dates: list[str],
        balances: list[float],
        transactions: list[dict],
        recurring_patterns: list[dict],
    ) -> np.ndarray:
        """
        Construit un vecteur de features pour chaque jour.

        Returns:
            numpy array shape (n_days, n_features)
        """
        df = pd.DataFrame({"date": pd.to_datetime(dates), "balance": balances})

        # ── Features temporelles de base ──────────────────────────────
        df["day_of_week"] = df["date"].dt.dayofweek
        df["day_of_month"] = df["date"].dt.day
        df["month"] = df["date"].dt.month
        df["is_weekend"] = (df["day_of_week"] >= 5).astype(int)
        df["is_month_end"] = (df["day_of_month"] >= 25).astype(int)
        df["is_month_start"] = (df["day_of_month"] <= 5).astype(int)

        # ── Features spécifiques freelances France ────────────────────

        # URSSAF — charges sociales trimestrielles (jan, avr, juil, oct)
        df["is_urssaf_month"] = df["month"].isin([1, 4, 7, 10]).astype(int)
        df["days_until_urssaf"] = df.apply(self._days_until_urssaf, axis=1)

        # TVA — trimestrielle (jan, avr, juil, oct)
        df["is_vat_period"] = df["month"].isin([1, 4, 7, 10]).astype(int)

        # Impôt sur le revenu — juillet et septembre
        df["is_income_tax_period"] = df["month"].isin([7, 9]).astype(int)

        # ── Features de tendance ──────────────────────────────────────

        df["rolling_7d_balance"] = df["balance"].rolling(7, min_periods=1).mean()
        df["rolling_30d_balance"] = df["balance"].rolling(30, min_periods=1).mean()
        df["daily_change"] = df["balance"].diff().fillna(0)
        df["volatility_30d"] = df["balance"].rolling(30, min_periods=7).std().fillna(0)

        # ── Features sur les flux ─────────────────────────────────────

        tx_df = pd.DataFrame(transactions) if transactions else pd.DataFrame()

        if not tx_df.empty and "date" in tx_df.columns and "amount" in tx_df.columns:
            tx_df["date"] = pd.to_datetime(tx_df["date"])
            tx_df["amount"] = pd.to_numeric(tx_df["amount"])

            daily_income = tx_df[tx_df["amount"] > 0].groupby("date")["amount"].sum()
            daily_expense = tx_df[tx_df["amount"] < 0].groupby("date")["amount"].sum().abs()

            df = df.merge(daily_income.rename("daily_income"), on="date", how="left")
            df = df.merge(daily_expense.rename("daily_expense"), on="date", how="left")
            df["daily_income"] = df["daily_income"].fillna(0)
            df["daily_expense"] = df["daily_expense"].fillna(0)

            df["avg_monthly_income_3m"] = (
                df["daily_income"].rolling(90, min_periods=30).sum() / 3
            )
        else:
            df["daily_income"] = 0.0
            df["daily_expense"] = 0.0
            df["avg_monthly_income_3m"] = 0.0

        # ── Features des patterns récurrents ──────────────────────────

        df["next_recurring_debit_7d"] = df.apply(
            lambda row: self._next_recurring_debit(row["date"], recurring_patterns, days=7),
            axis=1,
        )

        # ── Normalisation ─────────────────────────────────────────────
        reference_balance = df["rolling_30d_balance"].replace(0, 1)
        df["balance_normalized"] = df["balance"] / reference_balance
        df["income_normalized"] = df["daily_income"] / (reference_balance.abs() + 1)
        df["expense_normalized"] = df["daily_expense"] / (reference_balance.abs() + 1)

        # ── Sélection des features finales ────────────────────────────
        feature_columns = [
            "balance_normalized",
            "rolling_7d_balance",
            "rolling_30d_balance",
            "daily_change",
            "volatility_30d",
            "income_normalized",
            "expense_normalized",
            "avg_monthly_income_3m",
            "day_of_week",
            "day_of_month",
            "month",
            "is_weekend",
            "is_month_end",
            "is_month_start",
            "is_urssaf_month",
            "days_until_urssaf",
            "is_vat_period",
            "is_income_tax_period",
            "next_recurring_debit_7d",
        ]

        return df[feature_columns].values.astype(np.float32)

    def _days_until_urssaf(self, row) -> int:
        """
        Calcule le nombre de jours jusqu'à la prochaine échéance URSSAF.
        Échéances : 1er janvier, 1er avril, 1er juillet, 1er octobre.
        """
        date = row["date"]
        urssaf_months = [1, 4, 7, 10]

        year = date.year
        for month in urssaf_months:
            target = datetime(year, month, 1)
            if target > date:
                return min((target - date).days, 90)

        return min((datetime(year + 1, 1, 1) - date).days, 90)

    def _next_recurring_debit(
        self, date: datetime, patterns: list[dict], days: int = 7
    ) -> float:
        """
        Somme des débits récurrents prévus dans les N prochains jours.
        """
        total = 0.0
        for pattern in patterns:
            if pattern.get("amount", 0) >= 0:
                continue  # débits uniquement

            day = pattern["day_of_month"]
            for d in range(1, days + 1):
                future_day = (date + pd.Timedelta(days=d)).day
                if future_day == day:
                    total += abs(pattern["amount"]) * pattern["confidence"]
                    break

        return round(total, 2)
