"""
FlowGuard — BridgeDataPreprocessor
Converts Bridge API transactions into daily balance time series
for TimesFM and the LSTM models.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from datetime import datetime


class BridgeDataPreprocessor:
    """
    Pipeline de preprocessing des données Bridge pour les modèles ML.

    Input : transactions Bridge (liste de dicts avec amount, date, category)
    Output : série temporelle de soldes journaliers
    """

    def transactions_to_daily_balances(
        self,
        transactions: list[dict],
        current_balance: float,
        start_date: str,
        end_date: str,
    ) -> tuple[list[str], list[float]]:
        """
        Reconstitue les soldes journaliers à partir des transactions Bridge.

        Args:
            transactions: liste de transactions Bridge
                [{"amount": -850.0, "date": "2025-09-15", "category": "TAXES_SOCIAL"}, ...]
            current_balance: solde actuel du compte (depuis Bridge)
            start_date: début de la période YYYY-MM-DD
            end_date: fin de la période (aujourd'hui) YYYY-MM-DD

        Returns:
            (dates, balances) — listes de même longueur
        """
        if not transactions:
            raise ValueError("Aucune transaction fournie")

        df = pd.DataFrame(transactions)
        df["date"] = pd.to_datetime(df["date"])
        df["amount"] = pd.to_numeric(df["amount"])

        # Agréger par jour
        daily_flow = df.groupby("date")["amount"].sum().reset_index()
        daily_flow = daily_flow.rename(columns={"amount": "flow"})

        # Créer une plage de dates continue
        date_range = pd.date_range(start=start_date, end=end_date, freq="D")
        date_df = pd.DataFrame({"date": date_range})

        # Merger avec les flux réels
        merged = date_df.merge(daily_flow, on="date", how="left")
        merged["flow"] = merged["flow"].fillna(0.0)

        # Reconstituer les soldes en remontant depuis le solde actuel
        flows = merged["flow"].values
        dates = merged["date"].dt.strftime("%Y-%m-%d").tolist()

        balances = np.zeros(len(flows))
        balances[-1] = current_balance

        for i in range(len(flows) - 2, -1, -1):
            balances[i] = balances[i + 1] - flows[i + 1]

        return dates, balances.tolist()

    def detect_recurring_patterns(
        self,
        transactions: list[dict],
    ) -> list[dict]:
        """
        Détecte les transactions récurrentes (URSSAF, loyer, abonnements…).
        Utilisé comme features supplémentaires pour le LSTM.

        Returns:
            liste de patterns [{label, amount, day_of_month, category, confidence}]
        """
        df = pd.DataFrame(transactions)
        if df.empty:
            return []

        df["date"] = pd.to_datetime(df["date"])
        df["amount_rounded"] = df["amount"].round(-1)
        df["day_of_month"] = df["date"].dt.day
        df["month"] = df["date"].dt.to_period("M")

        patterns = []

        grouped = df.groupby(["amount_rounded", "day_of_month"])

        for (amount, day), group in grouped:
            months = group["month"].nunique()

            if months >= 3:
                confidence = min(1.0, months / 12)
                patterns.append({
                    "label": group["label"].mode()[0] if "label" in group.columns else "Unknown",
                    "amount": float(amount),
                    "day_of_month": int(day),
                    "category": group["category"].mode()[0] if "category" in group.columns else "OTHER",
                    "confidence": round(confidence, 2),
                    "months_observed": int(months),
                })

        return sorted(patterns, key=lambda x: x["confidence"], reverse=True)
