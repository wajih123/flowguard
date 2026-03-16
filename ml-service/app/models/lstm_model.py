"""
FlowGuard — TreasuryLSTM: bidirectional LSTM with temporal attention.

Architecture choices:
- Bidirectional: captures forward (income build-up) AND backward context
  (post-URSSAF recovery patterns)
- 2 layers: sufficient for 90-day sequences without overfitting on small datasets
- Dropout 0.3 variational (Gal & Ghahramani): active at inference for MC dropout
- LayerNorm (not BatchNorm): works correctly with variable-length financial sequences
- Temporal attention: learns WHICH past days drive the forecast
"""
from __future__ import annotations


import numpy as np
import torch
import torch.nn as nn
import structlog

from app.domain import UncertaintyResult

log = structlog.get_logger()

INPUT_SIZE = 15   # 15 daily features (see __init__ docstring)


class TemporalAttention(nn.Module):
    """
    Luong dot-product attention over LSTM hidden states.
    Learns which past days are most predictive of the future.
    """

    def __init__(self, hidden_size: int) -> None:
        super().__init__()
        # Bidirectional doubles the actual hidden size
        self.hidden_size = hidden_size * 2
        self.attn = nn.Linear(self.hidden_size, self.hidden_size, bias=False)

    def forward(
        self,
        query: torch.Tensor,       # (batch, hidden_size * 2)
        keys: torch.Tensor,        # (batch, seq_len, hidden_size * 2)
    ) -> tuple[torch.Tensor, torch.Tensor]:
        """
        Returns:
            context      : (batch, hidden_size * 2)  — attended representation
            attn_weights : (batch, seq_len)           — interpretable day weights
        """
        # Luong dot-product: score = query · key^T
        scores = torch.bmm(keys, self.attn(query).unsqueeze(2)).squeeze(2)  # (batch, seq_len)
        weights = torch.softmax(scores, dim=-1)                              # (batch, seq_len)
        context = torch.bmm(weights.unsqueeze(1), keys).squeeze(1)          # (batch, hidden*2)
        return context, weights


class TreasuryLSTM(nn.Module):
    """
    Primary cash-flow prediction model for users with ≥ 90d of history.

    Input : (batch, seq_len, 15) — 15 daily features
    Output: (batch, horizon)     — predicted daily balance

    The 15 features per timestep:
      [0]  daily_balance (normalized per user)
      [1]  daily_net_flow
      [2]  is_recurring_outflow
      [3]  recurring_amount (normalized)
      [4]  days_since_last_income (0–1)
      [5]  urssaf_due_in_7d
      [6]  urssaf_due_in_30d
      [7]  tva_due_in_7d
      [8]  tva_due_in_30d
      [9]  is_weekend
      [10] is_french_holiday
      [11] month_end_proximity (0.0–1.0)
      [12] quarter_end
      [13] income_volatility (static user feature)
      [14] avg_monthly_expenses (normalized)
    """

    def __init__(
        self,
        input_size: int = INPUT_SIZE,
        hidden_size: int = 128,
        num_layers: int = 2,
        dropout: float = 0.3,
        horizon: int = 90,
        use_attention: bool = True,
    ) -> None:
        super().__init__()
        self.hidden_size = hidden_size
        self.num_layers = num_layers
        self.horizon = horizon
        self.use_attention = use_attention

        # Bidirectional LSTM — output hidden dim = hidden_size * 2
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )

        # Variational dropout (applied to LSTM output)
        self.dropout = nn.Dropout(p=dropout)

        # Layer normalization (not batch norm — stable with variable-length sequences)
        self.layer_norm = nn.LayerNorm(hidden_size * 2)

        # Temporal attention
        if use_attention:
            self.attention = TemporalAttention(hidden_size)

        # Projection head: (hidden*2) → horizon
        combined_size = hidden_size * 2
        if use_attention:
            combined_size *= 2  # concat(last_hidden, context)

        self.fc = nn.Sequential(
            nn.Linear(combined_size, 256),
            nn.GELU(),
            nn.Dropout(p=dropout),
            nn.Linear(256, horizon),
        )

    def forward(
        self,
        x: torch.Tensor,
        return_attention: bool = False,
    ) -> tuple[torch.Tensor, dict]:
        """
        Args:
            x : (batch, seq_len, input_size)

        Returns:
            predictions : (batch, horizon)
            metadata    : {attention_weights, hidden_states, used_layers}
        """
        # LSTM forward
        lstm_out, (hn, _) = self.lstm(x)  # lstm_out: (batch, seq_len, hidden*2)
        lstm_out = self.layer_norm(lstm_out)
        lstm_out = self.dropout(lstm_out)

        # Last timestep hidden state (concatenate both directions)
        last_hidden = lstm_out[:, -1, :]  # (batch, hidden*2)

        metadata: dict = {"used_layers": self.num_layers}

        if self.use_attention:
            context, attn_weights = self.attention(last_hidden, lstm_out)
            metadata["attention_weights"] = attn_weights.detach()
            combined = torch.cat([last_hidden, context], dim=-1)
        else:
            combined = last_hidden
            metadata["attention_weights"] = None

        metadata["hidden_states"] = last_hidden.detach()

        predictions = self.fc(combined)  # (batch, horizon)
        return predictions, metadata

    # ── Monte Carlo Dropout uncertainty estimation ─────────────────────────────

    def predict_with_uncertainty(
        self,
        x: torch.Tensor,
        n_samples: int = 50,
        horizon: int = 90,
    ) -> UncertaintyResult:
        """
        Monte Carlo Dropout (Gal & Ghahramani 2016).
        Runs N=50 forward passes with dropout ENABLED at inference.
        Each pass is a sample from the approximate posterior.

        RULE: Alerts use p25 (pessimistic), NEVER the mean.
        This makes alerts conservative 75% of the time — correct
        for a credit product.
        """
        samples = []
        with torch.no_grad():
            self.train()  # Enable dropout
            for _ in range(n_samples):
                pred, _ = self.forward(x)
                samples.append(pred.cpu().numpy())
            self.eval()   # Restore eval mode

        samples_arr = np.stack(samples, axis=0)  # (n_samples, batch, horizon)
        # Squeeze batch dim for single prediction
        if samples_arr.ndim == 3 and samples_arr.shape[1] == 1:
            samples_arr = samples_arr[:, 0, :]   # (n_samples, horizon)

        return UncertaintyResult(
            mean_prediction=np.mean(samples_arr, axis=0),
            std_prediction=np.std(samples_arr, axis=0),
            p5_prediction=np.percentile(samples_arr, 5, axis=0),
            p25_prediction=np.percentile(samples_arr, 25, axis=0),
            p75_prediction=np.percentile(samples_arr, 75, axis=0),
            p95_prediction=np.percentile(samples_arr, 95, axis=0),
        )
