"""
FlowGuard — LSTM Predictor (Phase 2)
Fine-tuné sur les patterns spécifiques des freelances français.
Fonctionne EN PARALLÈLE de TimesFM — pas en remplacement.

Architecture: BiLSTM bidirectionnel + attention + 3 têtes de sortie (point, p25, p75).
"""
from __future__ import annotations

import logging
import os
from datetime import datetime, timedelta
from typing import Optional

import numpy as np
import torch
import torch.nn as nn

from app.models.timesfm_predictor import PredictionResult

logger = logging.getLogger(__name__)

# ── Hyperparamètres ─────────────────────────────────────────────────────────
N_FEATURES = 19        # Nombre de features du FreelanceFeatureEngineer
SEQUENCE_LENGTH = 90   # 90 jours de contexte
HIDDEN_SIZE = 128
N_LAYERS = 2
DROPOUT = 0.2


class FlowGuardLSTM(nn.Module):
    """
    Architecture LSTM pour la prédiction de trésorerie.

    Design :
    - 2 couches LSTM bidirectionnelles (capture patterns aller-retour)
    - Attention mechanism (pondère les jours les plus informatifs)
    - Têtes de sortie séparées : point forecast + p25 + p75
    """

    def __init__(
        self,
        n_features: int = N_FEATURES,
        hidden_size: int = HIDDEN_SIZE,
        n_layers: int = N_LAYERS,
        horizon: int = 90,
        dropout: float = DROPOUT,
    ) -> None:
        super().__init__()

        self.hidden_size = hidden_size
        self.n_layers = n_layers
        self.horizon = horizon

        # BiLSTM
        self.lstm = nn.LSTM(
            input_size=n_features,
            hidden_size=hidden_size,
            num_layers=n_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if n_layers > 1 else 0.0,
        )

        lstm_out_size = hidden_size * 2  # x2 bidirectionnel

        # Attention
        self.attention = nn.Sequential(
            nn.Linear(lstm_out_size, 64),
            nn.Tanh(),
            nn.Linear(64, 1),
        )

        self.dropout = nn.Dropout(dropout)

        # 3 têtes de sortie
        self.head_point = nn.Sequential(
            nn.Linear(lstm_out_size, 128),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(128, horizon),
        )
        self.head_p25 = nn.Sequential(
            nn.Linear(lstm_out_size, 64),
            nn.ReLU(),
            nn.Linear(64, horizon),
        )
        self.head_p75 = nn.Sequential(
            nn.Linear(lstm_out_size, 64),
            nn.ReLU(),
            nn.Linear(64, horizon),
        )

    def forward(
        self, x: torch.Tensor
    ) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        """
        Args:
            x: tensor shape (batch, seq_len, n_features)

        Returns:
            (point, p25, p75) — chacun shape (batch, horizon)
        """
        lstm_out, _ = self.lstm(x)                          # (batch, seq, hidden*2)

        # Attention-weighted pooling
        attn_w = self.attention(lstm_out)                   # (batch, seq, 1)
        attn_w = torch.softmax(attn_w, dim=1)
        context = (lstm_out * attn_w).sum(dim=1)            # (batch, hidden*2)
        context = self.dropout(context)

        point = self.head_point(context)
        p25 = self.head_p25(context)
        p75 = self.head_p75(context)

        # Garantir p25 <= point <= p75
        p25 = torch.minimum(p25, point)
        p75 = torch.maximum(p75, point)

        return point, p25, p75


class LSTMPredictor:
    """
    Wraps FlowGuardLSTM pour l'inférence.
    Chargé automatiquement si un checkpoint existe.
    """

    MODEL_PATH = os.getenv("LSTM_V3_MODEL_PATH", "models/checkpoints/flowguard_lstm_v3.pt")

    def __init__(self, horizon: int = 90) -> None:
        self.horizon = horizon
        self.model: Optional[FlowGuardLSTM] = None
        self.is_loaded = False
        self._load()

    def _load(self) -> None:
        if not os.path.exists(self.MODEL_PATH):
            logger.info("LSTM v3 checkpoint not found — LSTM inactive until trained")
            return

        try:
            checkpoint = torch.load(self.MODEL_PATH, map_location="cpu", weights_only=True)
            cfg = checkpoint.get("model_config", {})
            self.model = FlowGuardLSTM(horizon=self.horizon, **cfg)
            self.model.load_state_dict(checkpoint["model_state_dict"])
            self.model.eval()
            self.is_loaded = True
            logger.info(
                "LSTM v3 loaded",
                val_mae=checkpoint.get("val_mae"),
                epoch=checkpoint.get("epoch"),
            )
        except Exception as exc:
            logger.warning("lstm_v3_load_failed", error=str(exc))

    def predict(
        self,
        account_id: str,
        feature_matrix: np.ndarray,   # shape (seq_len, n_features)
        dates_history: list[str],
        horizon_days: int = 30,
    ) -> PredictionResult:
        """
        Génère une prédiction via le LSTM FlowGuard.

        Args:
            feature_matrix: features du FreelanceFeatureEngineer (seq_len, 19)
            dates_history: dates de l'historique
            horizon_days: 30 / 60 / 90
        """
        if not self.is_loaded or self.model is None:
            raise RuntimeError("LSTM not loaded — checkpoint manquant")

        # Prendre les SEQUENCE_LENGTH dernières lignes
        seq = feature_matrix[-SEQUENCE_LENGTH:]
        if len(seq) < SEQUENCE_LENGTH:
            # Padding avec la première ligne si historique trop court
            pad = np.tile(seq[0], (SEQUENCE_LENGTH - len(seq), 1))
            seq = np.vstack([pad, seq])

        x = torch.tensor(seq, dtype=torch.float32).unsqueeze(0)  # (1, seq, features)

        with torch.no_grad():
            point, p25_t, p75_t = self.model(x)

        predictions = point[0, :horizon_days].numpy()
        p25 = p25_t[0, :horizon_days].numpy()
        p75 = p75_t[0, :horizon_days].numpy()

        last_date = datetime.strptime(dates_history[-1], "%Y-%m-%d")
        future_dates = [
            (last_date + timedelta(days=i + 1)).strftime("%Y-%m-%d")
            for i in range(horizon_days)
        ]

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

        return PredictionResult(
            account_id=account_id,
            horizon_days=horizon_days,
            daily_balances=daily_data,
            min_balance=round(min_balance, 2),
            min_balance_date=future_dates[min_idx],
            deficit_predicted=min_balance < 0,
            deficit_amount=round(min_balance, 2) if min_balance < 0 else None,
            deficit_date=future_dates[min_idx] if min_balance < 0 else None,
            confidence_score=0.75,       # static for now; updated after MAE evaluation
            confidence_label="Indicatif",
            estimated_error_eur=100.0,
            model_used="lstm_v3",
        )
