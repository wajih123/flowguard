"""
FlowGuard — EnsemblePredictor v3 (TimesFM primary)
Combine TimesFM et LSTM FlowGuard pour une prédiction optimale.

Stratégie de pondération :
  Phase MVP   (< 100 users avec 90j)  : TimesFM 100% — LSTM pas encore entraîné
  Phase Beta  (100-1 000 users)       : TimesFM 70% + LSTM 30%
  Phase Prod  (1 000+ users)          : pondération dynamique selon MAE historique
"""
from __future__ import annotations

import logging
from typing import Optional

import numpy as np

from app.models.timesfm_predictor import PredictionResult, TimesFMPredictor

logger = logging.getLogger(__name__)


class EnsemblePredictor:
    """
    Orchestre TimesFM et LSTM pour la prédiction de trésorerie.

    Au démarrage:
    - TimesFM est chargé (zero-shot, toujours disponible)
    - LSTM est None jusqu'à ce qu'un checkpoint soit disponible

    Appel .update_weights_from_mae() après chaque évaluation mensuelle
    pour ajuster la pondération dynamiquement.
    """

    def __init__(self) -> None:
        self.timesfm = TimesFMPredictor()
        self.lstm = None            # LSTMPredictor — chargé après Phase 2
        self.timesfm_weight: float = 1.0
        self.lstm_weight: float = 0.0
        self.timesfm_mae: Optional[float] = None
        self.lstm_mae: Optional[float] = None

    def predict(
        self,
        account_id: str,
        daily_balances: list[float],
        dates: list[str],
        horizon_days: int = 30,
    ) -> PredictionResult:
        """
        Prédit en utilisant le meilleur modèle disponible.

        Si LSTM non disponible → TimesFM uniquement.
        Si les deux disponibles → ensemble pondéré.
        """
        # ── TimesFM only ──────────────────────────────────────────────
        if self.lstm is None or self.lstm_weight == 0.0:
            result = self.timesfm.predict(
                account_id, daily_balances, dates, horizon_days
            )
            return result

        # ── Ensemble ──────────────────────────────────────────────────
        timesfm_result = self.timesfm.predict(
            account_id, daily_balances, dates, horizon_days
        )

        try:
            lstm_result = self.lstm.predict(
                account_id=account_id,
                feature_matrix=self._build_feature_matrix(daily_balances),
                dates_history=dates,
                horizon_days=horizon_days,
            )
        except Exception as exc:
            logger.warning("lstm_predict_failed — using TimesFM only", error=str(exc))
            return timesfm_result

        combined = []
        for tf_day, lstm_day in zip(
            timesfm_result.daily_balances, lstm_result.daily_balances
        ):
            combined.append({
                "date": tf_day["date"],
                "balance": round(
                    self.timesfm_weight * tf_day["balance"]
                    + self.lstm_weight * lstm_day["balance"],
                    2,
                ),
                "p25": round(
                    self.timesfm_weight * tf_day["p25"]
                    + self.lstm_weight * lstm_day["p25"],
                    2,
                ),
                "p75": round(
                    self.timesfm_weight * tf_day["p75"]
                    + self.lstm_weight * lstm_day["p75"],
                    2,
                ),
            })

        min_balance = min(d["balance"] for d in combined)
        min_date = min(combined, key=lambda d: d["balance"])["date"]

        return PredictionResult(
            account_id=account_id,
            horizon_days=horizon_days,
            daily_balances=combined,
            min_balance=min_balance,
            min_balance_date=min_date,
            deficit_predicted=min_balance < 0,
            deficit_amount=round(min_balance, 2) if min_balance < 0 else None,
            deficit_date=min_date if min_balance < 0 else None,
            confidence_score=max(
                timesfm_result.confidence_score, lstm_result.confidence_score
            ),
            confidence_label=timesfm_result.confidence_label,
            estimated_error_eur=min(
                timesfm_result.estimated_error_eur,
                lstm_result.estimated_error_eur,
            ),
            model_used=(
                f"ensemble(timesfm={self.timesfm_weight:.0%},"
                f"lstm={self.lstm_weight:.0%})"
            ),
        )

    def update_weights_from_mae(self, timesfm_mae: float, lstm_mae: float) -> None:
        """
        Ajuste les poids selon les MAE respectives.
        Le modèle le plus précis reçoit plus de poids.
        Appelé après chaque évaluation mensuelle.
        """
        self.timesfm_mae = timesfm_mae
        self.lstm_mae = lstm_mae

        total = timesfm_mae + lstm_mae
        if total == 0:
            return

        # Poids inversement proportionnel à l'erreur
        self.timesfm_weight = round(lstm_mae / total, 2)
        self.lstm_weight = round(timesfm_mae / total, 2)

        logger.info(
            "ensemble_weights_updated",
            timesfm_weight=self.timesfm_weight,
            timesfm_mae=timesfm_mae,
            lstm_weight=self.lstm_weight,
            lstm_mae=lstm_mae,
        )

    def enable_lstm(self, lstm_predictor) -> None:
        """
        Active le LSTM dans l'ensemble une fois entraîné et évalué.
        Appelé par le scheduler après validation MAE.
        """
        self.lstm = lstm_predictor
        # Poids initiaux Phase Beta
        self.timesfm_weight = 0.70
        self.lstm_weight = 0.30
        logger.info("lstm_enabled_in_ensemble", timesfm=0.70, lstm=0.30)

    # ── Helpers ──────────────────────────────────────────────────────────────

    def _build_feature_matrix(self, daily_balances: list[float]) -> np.ndarray:
        """
        Construit une feature matrix minimale depuis les soldes seuls
        quand les transactions complètes ne sont pas disponibles.
        Le FreelanceFeatureEngineer est utilisé quand les transactions sont fournies.
        """
        n = len(daily_balances)
        balances = np.array(daily_balances, dtype=np.float32)
        # 19-feature placeholder — colonnes 0 et 2 sont les vraies balances
        features = np.zeros((n, 19), dtype=np.float32)
        features[:, 0] = balances / (np.mean(np.abs(balances)) + 1)  # balance_normalized
        features[:, 1] = balances  # rolling_7d (approximation)
        features[:, 2] = balances  # rolling_30d (approximation)
        return features
