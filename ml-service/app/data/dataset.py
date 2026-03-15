"""
FlowGuard — TreasuryForecastDataset
Dataset PyTorch pour l'entraînement du LSTM FlowGuard (Phase 2).

RGPD : données pseudonymisées avant création du dataset.
Le user_id est remplacé par un hash non réversible.
Aucune donnée PII (nom, email, IBAN) n'est incluse.
"""
from __future__ import annotations

import numpy as np
import torch
from torch.utils.data import Dataset


class TreasuryForecastDataset(Dataset):
    """
    Dataset de séries temporelles de trésorerie (fenêtres glissantes).

    RGPD : les données sont pseudonymisées avant création du dataset.
    Aucun PII n'est stocké ici.
    """

    def __init__(
        self,
        features: np.ndarray,         # (n_samples, seq_len, n_features)
        targets: np.ndarray,           # (n_samples, horizon)
        targets_p25: np.ndarray,       # (n_samples, horizon)
        targets_p75: np.ndarray,       # (n_samples, horizon)
        balance_scale: np.ndarray,     # (n_samples,) — scale pour dénormalisation
    ) -> None:
        assert len(features) == len(targets), "features / targets length mismatch"

        self.features = torch.FloatTensor(features)
        self.targets = torch.FloatTensor(targets)
        self.targets_p25 = torch.FloatTensor(targets_p25)
        self.targets_p75 = torch.FloatTensor(targets_p75)
        self.balance_scale = torch.FloatTensor(balance_scale)

    def __len__(self) -> int:
        return len(self.features)

    def __getitem__(self, idx: int) -> dict:
        return {
            "features": self.features[idx],
            "target": self.targets[idx],
            "target_p25": self.targets_p25[idx],
            "target_p75": self.targets_p75[idx],
            "scale": self.balance_scale[idx],
        }

    @staticmethod
    def create_sliding_windows(
        features: np.ndarray,    # (n_days, n_features)
        balances: np.ndarray,    # (n_days,)
        seq_length: int = 90,
        horizon: int = 30,
        stride: int = 7,
    ) -> tuple[np.ndarray, np.ndarray]:
        """
        Crée des fenêtres glissantes pour l'entraînement.

        Exemple seq_length=90, horizon=30, stride=7 :
          Fenêtre 1 : jours 0-89  → cible jours 90-119
          Fenêtre 2 : jours 7-96  → cible jours 97-126
          ...

        Args:
            stride: décalage entre fenêtres (7 = une fenêtre/semaine)

        Returns:
            (X, y) — arrays of shape (n_windows, seq_length, n_features)
                     and (n_windows, horizon)
        """
        X: list[np.ndarray] = []
        y: list[np.ndarray] = []

        total_length = seq_length + horizon

        for start in range(0, len(features) - total_length + 1, stride):
            x_window = features[start : start + seq_length]
            y_window = balances[start + seq_length : start + total_length]
            X.append(x_window)
            y.append(y_window)

        if not X:
            return np.empty((0, seq_length, features.shape[1]), dtype=np.float32), \
                   np.empty((0, horizon), dtype=np.float32)

        return np.array(X, dtype=np.float32), np.array(y, dtype=np.float32)
