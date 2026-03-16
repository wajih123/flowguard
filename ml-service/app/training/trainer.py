"""
LSTM Trainer — WeightedMAELoss, sliding-window dataset, training loop,
segmented evaluation, model versioning and rollback.
"""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Optional

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader, Dataset

from app.db import (
    promote_model_version,
    save_model_version,
)
from app.domain import (
    EvaluationReport,
    TrainingResult,
    UserProfile,
)
from app.models.lstm_model import TreasuryLSTM

log = logging.getLogger(__name__)


class WeightedMAELoss(nn.Module):
    """
    Weighted MAE: first 7 days → weight 3.0, 7-30 days → 1.5, 30+ → 1.0.
    Penalises short-term errors more heavily (operational cash-flow window).
    """

    def __init__(self, horizon: int = 90) -> None:
        super().__init__()
        weights = np.ones(horizon, dtype=np.float32)
        weights[:7] = 3.0
        weights[7:30] = 1.5
        self.register_buffer("weights", torch.tensor(weights))

    def forward(self, predicted: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        # predicted, target: (batch, horizon)
        mae = torch.abs(predicted - target)  # (batch, horizon)
        return (mae * self.weights).mean()


class TreasuryWindowDataset(Dataset):
    """
    Sliding-window dataset.
    input  : (seq_len, n_features) windows
    target : (horizon,) normalised balance values
    """

    def __init__(
        self,
        feature_matrices: list[np.ndarray],
        balance_series: list[np.ndarray],
        seq_len: int = 90,
        horizon: int = 90,
        stride: int = 7,
    ) -> None:
        self.samples: list[tuple[np.ndarray, np.ndarray]] = []
        for fm, bs in zip(feature_matrices, balance_series):
            n = len(bs)
            start = 0
            while start + seq_len + horizon <= n:
                x = fm[start : start + seq_len]  # (seq_len, feats)
                y = bs[start + seq_len : start + seq_len + horizon]  # (horizon,)
                self.samples.append((x.astype(np.float32), y.astype(np.float32)))
                start += stride

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int):
        x, y = self.samples[idx]
        return torch.tensor(x), torch.tensor(y)


def _augment_sample(x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Balance shift ±15%, scale ×[0.7,1.3], time jitter ±2d (feature shift)."""
    # Balance shift
    shift = np.random.uniform(-0.15, 0.15) * np.abs(y.mean() + 1e-6)
    y_aug = y + shift

    # Scale
    scale = np.random.uniform(0.7, 1.3)
    y_aug *= scale

    # Time jitter on feature matrix (first feature is balance — jitter by ≤2 rows)
    jitter = np.random.randint(-2, 3)
    if jitter > 0:
        x_aug = np.concatenate([x[jitter:], x[-jitter:]], axis=0)
    elif jitter < 0:
        x_aug = np.concatenate([x[:jitter], x[:-jitter]], axis=0)
    else:
        x_aug = x

    return x_aug, y_aug


class LSTMTrainer:
    """
    Full training loop for TreasuryLSTM.
    Supports augmentation when n_users < 500.
    """

    def __init__(
        self,
        seq_len: int = 90,
        horizon: int = 90,
        batch_size: int = 64,
        max_epochs: int = 150,
        lr: float = 1e-3,
        weight_decay: float = 1e-4,
        early_stop_patience: int = 15,
        device: str = "cpu",
    ) -> None:
        self.seq_len = seq_len
        self.horizon = horizon
        self.batch_size = batch_size
        self.max_epochs = max_epochs
        self.lr = lr
        self.weight_decay = weight_decay
        self.early_stop_patience = early_stop_patience
        self.device = torch.device(device)

    def build_dataset(
        self,
        feature_matrices: list[np.ndarray],
        balance_series: list[np.ndarray],
        augment: bool = False,
        stride: int = 7,
    ) -> TreasuryWindowDataset:
        if augment:
            aug_fms, aug_bss = [], []
            for fm, bs in zip(feature_matrices, balance_series):
                aug_fms.append(fm)
                aug_bss.append(bs)
                # Add 2 augmented copies per user
                for _ in range(2):
                    n = len(bs)
                    fa, ba = np.copy(fm), np.copy(bs)
                    # Sample window and augment
                    if n >= self.seq_len + self.horizon:
                        start = np.random.randint(0, n - self.seq_len - self.horizon + 1)
                        xa, ya = _augment_sample(
                            fa[start : start + self.seq_len],
                            ba[start + self.seq_len : start + self.seq_len + self.horizon],
                        )
                        # Pad back to full arrays (simplified: use same aug for all windows)
                        aug_fms.append(fa)
                        aug_bss.append(ba + (ya.mean() - ba.mean()))
            feature_matrices = aug_fms
            balance_series = aug_bss

        return TreasuryWindowDataset(
            feature_matrices, balance_series, self.seq_len, self.horizon, stride
        )

    def train(
        self,
        train_fms: list[np.ndarray],
        train_bss: list[np.ndarray],
        val_fms: list[np.ndarray],
        val_bss: list[np.ndarray],
        n_users: int,
        model_config: Optional[dict] = None,
    ) -> tuple[TreasuryLSTM, list[float], list[float]]:
        """
        Train LSTM. Returns (model, train_losses, val_losses).
        Uses augmentation when n_users < 500.
        """
        if model_config is None:
            model_config = {
                "input_size": 15,
                "hidden_size": 128,
                "num_layers": 2,
                "dropout": 0.3,
                "horizon": self.horizon,
                "bidirectional": True,
            }

        model = TreasuryLSTM(**model_config).to(self.device)
        criterion = WeightedMAELoss(horizon=self.horizon)
        optimizer = optim.AdamW(
            model.parameters(), lr=self.lr, weight_decay=self.weight_decay
        )
        scheduler = optim.lr_scheduler.CosineAnnealingWarmRestarts(
            optimizer, T_0=20, T_mult=2
        )

        augment = n_users < 500
        train_ds = self.build_dataset(train_fms, train_bss, augment=augment)
        val_ds = self.build_dataset(val_fms, val_bss, augment=False)

        train_loader = DataLoader(
            train_ds, batch_size=self.batch_size, shuffle=True, drop_last=True
        )
        val_loader = DataLoader(val_ds, batch_size=self.batch_size, shuffle=False)

        best_val_mae = float("inf")
        best_state: Optional[dict] = None
        patience_counter = 0
        train_losses: list[float] = []
        val_losses: list[float] = []

        for epoch in range(self.max_epochs):
            # --- Train ---
            model.train()
            epoch_loss = 0.0
            for x_batch, y_batch in train_loader:
                x_batch = x_batch.to(self.device)
                y_batch = y_batch.to(self.device)
                optimizer.zero_grad()
                preds, _ = model(x_batch)
                loss = criterion(preds, y_batch)
                loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                optimizer.step()
                epoch_loss += loss.item()
            scheduler.step()

            avg_train = epoch_loss / max(len(train_loader), 1)
            train_losses.append(avg_train)

            # --- Validate ---
            model.eval()
            val_loss = 0.0
            with torch.no_grad():
                for x_batch, y_batch in val_loader:
                    x_batch = x_batch.to(self.device)
                    y_batch = y_batch.to(self.device)
                    preds, _ = model(x_batch)
                    loss = criterion(preds, y_batch)
                    val_loss += loss.item()

            avg_val = val_loss / max(len(val_loader), 1)
            val_losses.append(avg_val)

            if avg_val < best_val_mae:
                best_val_mae = avg_val
                import copy
                best_state = copy.deepcopy(model.state_dict())
                patience_counter = 0
            else:
                patience_counter += 1

            if epoch % 10 == 0:
                log.info(
                    f"Epoch {epoch:03d} — train_mae={avg_train:.1f} val_mae={avg_val:.1f} "
                    f"patience={patience_counter}/{self.early_stop_patience}"
                )

            if patience_counter >= self.early_stop_patience:
                log.info(f"Early stopping at epoch {epoch}")
                break

        # Restore best
        if best_state:
            model.load_state_dict(best_state)
        model.eval()
        return model, train_losses, val_losses

    def evaluate(
        self,
        model: TreasuryLSTM,
        val_fms: list[np.ndarray],
        val_bss: list[np.ndarray],
        user_profiles: Optional[list[UserProfile]] = None,
    ) -> EvaluationReport:
        """
        Segmented MAE by user profile and history bucket.
        Computes deficit_recall = TP / (TP + FN) where positive = predicted_balance < 0.
        """
        model.eval()
        all_preds: list[np.ndarray] = []
        all_targets: list[np.ndarray] = []

        with torch.no_grad():
            for fm, bs in zip(val_fms, val_bss):
                if len(bs) < self.seq_len + self.horizon:
                    continue
                x = torch.tensor(fm[-self.seq_len :], dtype=torch.float32).unsqueeze(0)
                preds, _ = model(x)
                all_preds.append(preds[0].numpy())
                all_targets.append(bs[-self.horizon :])

        if not all_preds:
            return EvaluationReport(mae_7d=9999.0, mae_30d=9999.0, mae_90d=9999.0, deficit_recall=0.0, passes_production_threshold=False)

        preds_arr = np.array(all_preds)     # (N, horizon)
        targets_arr = np.array(all_targets) # (N, horizon)

        mae_7d = float(np.mean(np.abs(preds_arr[:, :7] - targets_arr[:, :7])))
        mae_30d = float(np.mean(np.abs(preds_arr[:, :30] - targets_arr[:, :30])))
        mae_90d = float(np.mean(np.abs(preds_arr - targets_arr)))

        # Deficit recall — based on 7-day window
        actual_deficit = targets_arr[:, :7].min(axis=1) < 0     # (N,)
        pred_deficit = preds_arr[:, :7].min(axis=1) < 0          # (N,)
        tp = float(np.sum(actual_deficit & pred_deficit))
        fn = float(np.sum(actual_deficit & ~pred_deficit))
        deficit_recall = tp / (tp + fn) if (tp + fn) > 0 else 1.0

        # Segmented MAE
        by_profile: dict[str, float] = {}
        if user_profiles:
            for profile in UserProfile:
                idxs = [i for i, p in enumerate(user_profiles) if p == profile]
                if idxs:
                    seg_preds = preds_arr[idxs]
                    seg_targets = targets_arr[idxs]
                    by_profile[profile.value] = float(
                        np.mean(np.abs(seg_preds - seg_targets))
                    )

        passes = mae_7d < 150.0 and deficit_recall > 0.75

        return EvaluationReport(
            mae_7d=mae_7d,
            mae_30d=mae_30d,
            mae_90d=mae_90d,
            deficit_recall=deficit_recall,
            passes_production_threshold=passes,
            by_profile=by_profile,
        )

    def save_model(
        self,
        model: TreasuryLSTM,
        eval_report: EvaluationReport,
        model_path: str,
        model_config: dict,
        n_users: int,
    ) -> TrainingResult:
        """
        Save model checkpoint to disk and DB.
        Promote to ACTIVE if passes_production_threshold.
        Maintain at most 3 versions (rollback).
        """
        import os

        os.makedirs(os.path.dirname(model_path) if os.path.dirname(model_path) else ".", exist_ok=True)
        torch.save(
            {
                "model_state_dict": model.state_dict(),
                "model_config": model_config,
                "eval_report": {
                    "mae_7d": eval_report.mae_7d,
                    "mae_30d": eval_report.mae_30d,
                    "mae_90d": eval_report.mae_90d,
                    "deficit_recall": eval_report.deficit_recall,
                },
                "trained_at": datetime.utcnow().isoformat(),
                "n_users": n_users,
            },
            model_path,
        )
        log.info(
            "model_saved",
            path=model_path,
            mae_7d=eval_report.mae_7d,
            passes=eval_report.passes_production_threshold,
        )

        version = save_model_version(
            model_path=model_path,
            mae_7d=eval_report.mae_7d,
            mae_30d=eval_report.mae_30d,
            n_users=n_users,
        )

        promoted = False
        if eval_report.passes_production_threshold:
            promoted = promote_model_version(version.id)
            log.info("model_promoted", version_id=version.id)
        else:
            log.warning(
                "model_not_promoted",
                mae_7d=eval_report.mae_7d,
                deficit_recall=eval_report.deficit_recall,
                reason="below_production_threshold",
            )

        return TrainingResult(
            version=version,
            evaluation=eval_report,
            promoted=promoted,
            model_path=model_path,
            trained_at=datetime.utcnow(),
            n_users=n_users,
        )
