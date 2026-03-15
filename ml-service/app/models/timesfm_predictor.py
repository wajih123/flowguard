"""
TimesFM Predictor — Wrapper FlowGuard
Modèle pré-entraîné Google Research (Apache 2.0)
Zero-shot : aucun entraînement requis

Attribution: TimesFM (Google Research)
License: Apache 2.0
Repo: https://github.com/google-research/timesfm
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Optional

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)


@dataclass
class PredictionResult:
    account_id: str
    horizon_days: int
    daily_balances: list[dict]      # [{date, balance, p25, p75}]
    min_balance: float
    min_balance_date: str
    deficit_predicted: bool
    deficit_amount: Optional[float]
    deficit_date: Optional[str]
    confidence_score: float
    confidence_label: str           # "Fiable" | "Indicatif" | "Estimation"
    estimated_error_eur: float
    model_used: str                 # "timesfm" | "lstm" | "ensemble"


class TimesFMPredictor:
    """
    Wrapper autour de TimesFM (Google Research, Apache 2.0).
    Utilisé comme prédicteur principal en phase MVP.

    Avantages :
    - Zero-shot : fonctionne dès le 1er jour sans données d'entraînement
    - Intervalles de confiance p10-p90 natifs
    - Tourne sur CPU (Hetzner CAX11 suffisant)
    - Gratuit, open source
    """

    MODEL_REPO = "google/timesfm-2.0-500m-pytorch"

    def __init__(self) -> None:
        self.model = None
        self.is_loaded = False
        self._load_error: Optional[str] = None

    def load(self) -> None:
        """
        Charge le modèle TimesFM depuis HuggingFace.
        Appelé au démarrage du service ML (une seule fois).
        ~500MB à télécharger la première fois.
        """
        try:
            import timesfm

            logger.info("Chargement TimesFM 2.0 depuis HuggingFace...")

            # timesfm >= 1.2.0 API
            self.model = timesfm.TimesFm(
                hparams=timesfm.TimesFmHparams(
                    backend="cpu",
                    per_core_batch_size=32,
                    horizon_len=128,   # max horizon — actual forecast uses slice
                    num_layers=20,
                    model_dims=1280,
                    use_positional_embedding=False,
                ),
                checkpoint=timesfm.TimesFmCheckpoint(
                    huggingface_repo_id=self.MODEL_REPO,
                ),
            )

            self.is_loaded = True
            logger.info("TimesFM 2.0 chargé avec succès")

        except Exception as exc:
            self._load_error = str(exc)
            logger.warning(
                "timesfm_load_failed — falling back to statistical baseline",
                error=str(exc),
            )

    def predict(
        self,
        account_id: str,
        daily_balances_history: list[float],
        dates_history: list[str],
        horizon_days: int = 30,
    ) -> PredictionResult:
        """
        Prédit le solde futur à partir de l'historique Bridge.

        Args:
            account_id: ID du compte FlowGuard
            daily_balances_history: soldes journaliers (min 14 jours)
            dates_history: dates correspondantes YYYY-MM-DD
            horizon_days: 30 / 60 / 90

        Returns:
            PredictionResult avec prévisions journalières + intervalles confiance
        """
        if len(daily_balances_history) < 14:
            raise ValueError(
                f"Minimum 14 jours d'historique requis. "
                f"Reçu : {len(daily_balances_history)} jours."
            )

        if not self.is_loaded:
            self.load()

        balances = self._clean_series(daily_balances_history)
        last_date = datetime.strptime(dates_history[-1], "%Y-%m-%d")
        future_dates = [
            (last_date + timedelta(days=i + 1)).strftime("%Y-%m-%d")
            for i in range(horizon_days)
        ]

        if self.is_loaded and self.model is not None:
            predictions, p25, p75 = self._run_timesfm(balances, horizon_days)
        else:
            # Statistical fallback when TimesFM is unavailable
            predictions, p25, p75 = self._statistical_forecast(balances, horizon_days)

        daily_data = [
            {
                "date": future_dates[i],
                "balance": round(float(predictions[i]), 2),
                "p25": round(float(p25[i]), 2),
                "p75": round(float(p75[i]), 2),
            }
            for i in range(horizon_days)
        ]

        min_balance = float(np.min(predictions))
        min_idx = int(np.argmin(predictions))
        min_date = future_dates[min_idx]
        deficit_predicted = min_balance < 0

        confidence_score, confidence_label, estimated_error = self._compute_confidence(
            history_length=len(daily_balances_history),
            balance_volatility=float(np.std(balances[-30:])),
            current_balance=balances[-1],
        )

        model_used = "timesfm" if (self.is_loaded and self.model is not None) else "statistical_fallback"

        return PredictionResult(
            account_id=account_id,
            horizon_days=horizon_days,
            daily_balances=daily_data,
            min_balance=round(min_balance, 2),
            min_balance_date=min_date,
            deficit_predicted=deficit_predicted,
            deficit_amount=round(min_balance, 2) if deficit_predicted else None,
            deficit_date=min_date if deficit_predicted else None,
            confidence_score=confidence_score,
            confidence_label=confidence_label,
            estimated_error_eur=estimated_error,
            model_used=model_used,
        )

    def _run_timesfm(
        self,
        balances: list[float],
        horizon_days: int,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        Appelle le modèle TimesFM et retourne (point, p25, p75).
        """
        inputs = [np.array(balances, dtype=np.float32)]
        freq = [0]  # 0 = haute fréquence (quotidien)

        # timesfm.forecast returns (point_forecast, quantile_forecast)
        # point_forecast shape: (n_series, horizon)
        # quantile_forecast shape: (n_series, horizon, n_quantiles)
        point_forecast, quantile_forecast = self.model.forecast(
            inputs,
            freq=freq,
        )

        predictions = point_forecast[0, :horizon_days]
        quantiles = quantile_forecast[0, :horizon_days, :]  # (horizon, n_quantiles)

        # TimesFM default quantiles: [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]
        # p25 ≈ index 1 (0.2), p75 ≈ index 6 (0.7)
        n_q = quantiles.shape[-1]
        p25_idx = max(0, min(1, n_q - 1))
        p75_idx = max(0, min(6, n_q - 1))
        p25 = quantiles[:, p25_idx]
        p75 = quantiles[:, p75_idx]

        return predictions, p25, p75

    def _statistical_forecast(
        self,
        balances: list[float],
        horizon_days: int,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """
        Fallback statistique simple (ETS-like) quand TimesFM n'est pas disponible.
        Utilise une tendance linéaire + saisonnalité hebdomadaire.
        """
        series = np.array(balances, dtype=np.float32)
        n = len(series)

        # Tendance sur les 30 derniers jours
        window = min(30, n)
        x = np.arange(window)
        y = series[-window:]
        slope = float(np.polyfit(x, y, 1)[0])

        last = series[-1]
        predictions = np.array(
            [last + slope * (i + 1) for i in range(horizon_days)], dtype=np.float32
        )

        # Intervalles basés sur la volatilité historique
        vol = float(np.std(series[-window:]))
        width = vol * 1.28  # ~80% interval
        p25 = predictions - width * 0.5
        p75 = predictions + width * 0.5

        return predictions, p25, p75

    def _clean_series(self, balances: list[float]) -> list[float]:
        """
        Nettoie la série temporelle :
        - Interpole les valeurs manquantes (NaN)
        - Atténue les pics anormaux (zscore > 5)
        """
        series = pd.Series(balances, dtype=float)
        series = series.interpolate(method="linear", limit_direction="both")

        zscore = (series - series.mean()) / (series.std() + 1e-8)
        outliers = zscore.abs() > 5
        if outliers.any():
            smoothed = series.rolling(7, center=True, min_periods=1).mean()
            series[outliers] = smoothed[outliers]

        return series.tolist()

    def _compute_confidence(
        self,
        history_length: int,
        balance_volatility: float,
        current_balance: float,
    ) -> tuple[float, str, float]:
        """
        Calcule le niveau de confiance de la prédiction.

        Règles FlowGuard :
        - Fiable (>85%)    : 90+ jours, faible volatilité
        - Indicatif (70-85%) : 30-90 jours, volatilité moyenne
        - Estimation (<70%) : moins de 30 jours ou forte volatilité
        """
        score = 1.0

        # Pénalité données insuffisantes
        if history_length < 30:
            score -= 0.35
        elif history_length < 60:
            score -= 0.15
        elif history_length < 90:
            score -= 0.05

        # Pénalité volatilité
        if current_balance != 0:
            volatility_ratio = balance_volatility / (abs(current_balance) + 1)
            if volatility_ratio > 0.8:
                score -= 0.25
            elif volatility_ratio > 0.4:
                score -= 0.10

        # Pénalité si fallback (TimesFM non disponible)
        if not self.is_loaded:
            score -= 0.10

        score = max(0.30, min(1.0, score))

        if score >= 0.85:
            label = "Fiable"
        elif score >= 0.70:
            label = "Indicatif"
        else:
            label = "Estimation"

        base_error = abs(current_balance) * (1 - score) * 0.3
        estimated_error = round(max(50.0, base_error), 2)

        return round(score, 3), label, estimated_error
