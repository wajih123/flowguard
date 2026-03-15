import torch
import torch.nn as nn
import numpy as np
import structlog

log = structlog.get_logger()

class TreasuryLSTM(nn.Module):
    """
    LSTM model for treasury prediction.
    Input:  sequence of daily balances (normalized)
    Output: predicted balances for next N days
    """
    def __init__(
        self,
        input_size: int = 1,
        hidden_size: int = 128,
        num_layers: int = 2,
        output_size: int = 30,
        dropout: float = 0.2
    ):
        super().__init__()
        self.hidden_size = hidden_size
        self.num_layers = num_layers

        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0.0
        )
        self.fc1 = nn.Linear(hidden_size, 64)
        self.relu = nn.ReLU()
        self.dropout = nn.Dropout(dropout)
        self.fc2 = nn.Linear(64, output_size)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # x shape: (batch, seq_len, input_size)
        h0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size)
        c0 = torch.zeros(self.num_layers, x.size(0), self.hidden_size)
        out, _ = self.lstm(x, (h0, c0))
        out = out[:, -1, :]  # Last timestep
        out = self.dropout(self.relu(self.fc1(out)))
        return self.fc2(out)


class TreasuryPredictor:
    """
    Wraps TreasuryLSTM with preprocessing, prediction, and analysis.
    Falls back to rule-based prediction if model not trained yet.
    """

    MODEL_PATH = "models/treasury_lstm.pt"
    SEQ_LEN    = 60  # Use last 60 days as input sequence

    def __init__(self):
        self.model: TreasuryLSTM | None = None
        self.scaler_mean: float = 0.0
        self.scaler_std: float  = 1.0
        self._load_model()

    def _load_model(self) -> None:
        """Load trained model if it exists, else use rule-based fallback."""
        import os
        if os.path.exists(self.MODEL_PATH):
            try:
                checkpoint = torch.load(self.MODEL_PATH, map_location="cpu")
                self.model = TreasuryLSTM(**checkpoint.get("model_config", {}))
                self.model.load_state_dict(checkpoint["model_state_dict"])
                self.model.eval()
                self.scaler_mean = checkpoint.get("scaler_mean", 0.0)
                self.scaler_std  = checkpoint.get("scaler_std", 1.0)
                log.info("LSTM model loaded", path=self.MODEL_PATH)
            except Exception as e:
                log.warning("Failed to load LSTM model, using fallback",
                            error=str(e))
                self.model = None
        else:
            log.info("No trained model found — using rule-based fallback")

    def predict(
        self,
        series: list[dict],   # [{"date": "2026-01-01", "balance": 1234.56}]
        horizon_days: int = 30
    ) -> tuple[list[dict], float]:
        """
        Predict future balances.
        Returns (predictions, confidence_score).
        predictions: [{"date": str, "balance": float}]
        """
        if len(series) < 7:
            raise ValueError(
                f"Minimum 7 jours d'historique requis, reçu {len(series)}"
            )
        if not 1 <= horizon_days <= 180:
            raise ValueError(
                f"horizon_days doit être entre 1 et 180, reçu {horizon_days}"
            )

        balances = [float(s["balance"]) for s in series]

        if self.model is not None and len(series) >= self.SEQ_LEN:
            return self._predict_lstm(balances, series, horizon_days)
        else:
            return self._predict_rule_based(balances, series, horizon_days)

    def _predict_lstm(
        self,
        balances: list[float],
        series: list[dict],
        horizon_days: int
    ) -> tuple[list[dict], float]:
        """LSTM-based prediction (when model is trained)."""
        from datetime import date, timedelta
        import pandas as pd

        last_date = date.fromisoformat(series[-1]["date"])

        # Normalize
        arr = np.array(balances[-self.SEQ_LEN:], dtype=np.float32)
        normalized = (arr - self.scaler_mean) / (self.scaler_std + 1e-8)

        # Build input tensor
        x = torch.tensor(normalized).unsqueeze(0).unsqueeze(-1)  # (1, seq, 1)

        # Predict
        with torch.no_grad():
            # For horizons > model output_size, predict iteratively
            predictions_normalized = []
            current_seq = normalized.copy()

            steps = (horizon_days + 29) // 30  # Number of 30-day chunks
            for _ in range(steps):
                inp = torch.tensor(current_seq[-self.SEQ_LEN:]).unsqueeze(0).unsqueeze(-1)
                out = self.model(inp).squeeze().numpy()
                predictions_normalized.extend(out[:min(30, horizon_days - len(predictions_normalized))])
                current_seq = np.concatenate([current_seq, out])

        predictions_normalized = predictions_normalized[:horizon_days]

        # Denormalize
        pred_balances = [
            round(float(p * self.scaler_std + self.scaler_mean), 2)
            for p in predictions_normalized
        ]

        result = [
            {
                "date": (last_date + timedelta(days=i+1)).isoformat(),
                "balance": b
            }
            for i, b in enumerate(pred_balances)
        ]

        confidence = min(0.95, 0.60 + len(series) / 500)
        return result, confidence

    def _predict_rule_based(
        self,
        balances: list[float],
        series: list[dict],
        horizon_days: int
    ) -> tuple[list[dict], float]:
        """
        Rule-based fallback prediction.
        Uses trend + seasonality + French monthly patterns.
        Used when model is not yet trained (cold start).
        """
        from datetime import date, timedelta

        last_date = date.fromisoformat(series[-1]["date"])
        last_balance = balances[-1]

        # Compute trend from last 30 days (or all available)
        window = balances[-min(30, len(balances)):]
        avg_daily_change = (window[-1] - window[0]) / len(window) if len(window) > 1 else 0.0

        # Volatility
        if len(window) > 1:
            changes = [window[i+1] - window[i] for i in range(len(window)-1)]
            volatility = float(np.std(changes))
        else:
            volatility = 50.0

        np.random.seed(42)  # Reproducible
        predictions = []
        current = last_balance

        for i in range(horizon_days):
            d = last_date + timedelta(days=i+1)
            dom = d.day  # day of month

            # French monthly patterns
            monthly = 0.0
            if dom == 1:   monthly = +2500.0   # Salary
            elif dom == 5: monthly = -800.0    # Rent
            elif dom in (10, 15, 25): monthly = -180.0  # Subscriptions/charges

            noise = float(np.random.normal(0, volatility * 0.20))
            current = current + avg_daily_change + monthly + noise
            predictions.append({
                "date": d.isoformat(),
                "balance": round(current, 2)
            })

        confidence = min(0.75, 0.40 + len(series) / 300)
        return predictions, confidence

    def detect_anomalies(self, series: list[dict]) -> list[dict]:
        """
        Z-score anomaly detection.
        Returns list of anomaly dicts with date, balance, z_score, message_fr.
        """
        if len(series) < 3:
            return []

        balances = np.array([float(s["balance"]) for s in series])
        mean = float(np.mean(balances))
        std  = float(np.std(balances))

        if std < 1e-6:
            return []

        anomalies = []
        for s in series:
            b = float(s["balance"])
            z = abs((b - mean) / std)
            if z > 2.5:
                anomalies.append({
                    "date":      s["date"],
                    "balance":   b,
                    "z_score":   round(z, 2),
                    "message_fr": (
                        f"Mouvement inhabituel détecté le {s['date']} "
                        f"— écart de {z:.1f}σ par rapport à la moyenne."
                    )
                })

        return anomalies

    def compute_health_score(
        self,
        history: list[dict],
        predictions: list[dict]
    ) -> int:
        """
        Financial health score 0–100.
        Considers prediction quality, trend, volatility, negative days.
        """
        score = 100

        # Penalize predicted negative days
        neg_days = sum(1 for p in predictions if float(p["balance"]) < 0)
        score -= min(40, neg_days * 5)

        # Penalize high volatility in history
        if len(history) > 2:
            bal = [float(h["balance"]) for h in history]
            changes = [abs(bal[i+1] - bal[i]) for i in range(len(bal)-1)]
            avg_vol = float(np.mean(changes))
            if avg_vol > 800:  score -= 15
            elif avg_vol > 400: score -= 8

        # Penalize negative trend
        if len(history) >= 10:
            first_half = np.mean([float(h["balance"]) for h in history[:len(history)//2]])
            second_half = np.mean([float(h["balance"]) for h in history[len(history)//2:]])
            if second_half < first_half * 0.9:
                score -= 15
            elif second_half > first_half * 1.1:
                score += 10  # Bonus: positive trend

        # Bonus: no negative predictions
        if neg_days == 0:
            score += 5

        return max(0, min(100, score))

    def detect_critical_points(self, predictions: list[dict]) -> list[dict]:
        """
        Find predicted days where balance < 0.
        Returns list with date, balance, urgency, days_until.
        """
        from datetime import date

        today = date.today()
        critical = []

        for p in predictions:
            if float(p["balance"]) < 0:
                pred_date = date.fromisoformat(p["date"])
                days_until = (pred_date - today).days
                critical.append({
                    "date":              p["date"],
                    "projected_balance": float(p["balance"]),
                    "urgency":           "IMMINENT" if days_until <= 7 else "UPCOMING",
                    "days_until":        days_until
                })

        return critical

    def train(
        self,
        training_data: list[dict],  # [{"account_id": str, "series": [...]}]
        epochs: int = 50
    ) -> dict:
        """
        Train the LSTM model on aggregated multi-account data.
        Called by POST /retrain endpoint.
        Returns training metrics.
        """
        import os
        from sklearn.preprocessing import StandardScaler
        from torch.utils.data import DataLoader, TensorDataset

        log.info("Starting LSTM training", accounts=len(training_data), epochs=epochs)

        # Prepare sequences from all accounts
        all_sequences = []
        all_targets   = []

        for account in training_data:
            series = account.get("series", [])
            if len(series) < self.SEQ_LEN + 30:
                continue

            balances = [float(s["balance"]) for s in series]

            # Create sliding window sequences
            for i in range(len(balances) - self.SEQ_LEN - 30):
                seq = balances[i : i + self.SEQ_LEN]
                target = balances[i + self.SEQ_LEN : i + self.SEQ_LEN + 30]
                all_sequences.append(seq)
                all_targets.append(target)

        if len(all_sequences) < 10:
            return {"status": "insufficient_data",
                    "message": "Pas assez de données pour entraîner le modèle."}

        # Normalize using separate flatten to avoid inhomogeneous array error
        # (all_sequences has seqs of length SEQ_LEN=60, all_targets of length 30)
        sequences_flat = np.array(all_sequences, dtype=np.float32).flatten()
        targets_flat   = np.array(all_targets,   dtype=np.float32).flatten()
        all_values = np.concatenate([sequences_flat, targets_flat])
        self.scaler_mean = float(np.mean(all_values))
        self.scaler_std  = float(np.std(all_values))

        X = np.array(all_sequences, dtype=np.float32)
        y = np.array(all_targets,   dtype=np.float32)
        X = (X - self.scaler_mean) / (self.scaler_std + 1e-8)
        y = (y - self.scaler_mean) / (self.scaler_std + 1e-8)

        X_t = torch.tensor(X).unsqueeze(-1)  # (N, seq_len, 1)
        y_t = torch.tensor(y)                # (N, 30)

        # Train/val split
        split = int(0.8 * len(X_t))
        train_ds = TensorDataset(X_t[:split], y_t[:split])
        val_ds   = TensorDataset(X_t[split:], y_t[split:])
        train_loader = DataLoader(train_ds, batch_size=32, shuffle=True)
        val_loader   = DataLoader(val_ds,   batch_size=32)

        # Model
        model_config = {"input_size": 1, "hidden_size": 128,
                        "num_layers": 2, "output_size": 30, "dropout": 0.2}
        model = TreasuryLSTM(**model_config)
        optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
        criterion = nn.MSELoss()
        scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
            optimizer, patience=5, factor=0.5
        )

        best_val_loss = float('inf')
        train_losses, val_losses = [], []

        for epoch in range(epochs):
            # Train
            model.train()
            train_loss = 0.0
            for xb, yb in train_loader:
                optimizer.zero_grad()
                pred = model(xb)
                loss = criterion(pred, yb)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                optimizer.step()
                train_loss += loss.item()
            train_loss /= len(train_loader)

            # Validate
            model.eval()
            val_loss = 0.0
            with torch.no_grad():
                for xb, yb in val_loader:
                    val_loss += criterion(model(xb), yb).item()
            val_loss /= len(val_loader)

            scheduler.step(val_loss)
            train_losses.append(train_loss)
            val_losses.append(val_loss)

            if val_loss < best_val_loss:
                best_val_loss = val_loss
                # Save best model
                os.makedirs("models", exist_ok=True)
                torch.save({
                    "model_state_dict": model.state_dict(),
                    "model_config":     model_config,
                    "scaler_mean":      self.scaler_mean,
                    "scaler_std":       self.scaler_std,
                    "epoch":            epoch,
                    "val_loss":         val_loss
                }, self.MODEL_PATH)

            if epoch % 10 == 0:
                log.info("Training progress",
                         epoch=epoch, train_loss=round(train_loss, 4),
                         val_loss=round(val_loss, 4))

        # Load best model
        self._load_model()

        # Compute MAE on validation set (in original scale)
        model.eval()
        all_preds, all_targets_np = [], []
        with torch.no_grad():
            for xb, yb in val_loader:
                p = model(xb).numpy()
                all_preds.extend((p * self.scaler_std + self.scaler_mean).tolist())
                all_targets_np.extend((yb.numpy() * self.scaler_std + self.scaler_mean).tolist())

        mae = float(np.mean(np.abs(
            np.array(all_preds) - np.array(all_targets_np)
        )))

        log.info("Training complete",
                 best_val_loss=round(best_val_loss, 4),
                 mae=round(mae, 2),
                 sequences=len(all_sequences))

        return {
            "status":        "success",
            "epochs_trained": epochs,
            "best_val_loss":  round(best_val_loss, 4),
            "mae_euros":      round(mae, 2),
            "sequences_used": len(all_sequences),
            "model_path":     self.MODEL_PATH
        }
