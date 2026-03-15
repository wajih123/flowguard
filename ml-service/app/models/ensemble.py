"""
FlowGuard — EnsemblePredictor + SanityChecker.

3-layer architecture:
  Layer 1: DataQualityPipeline (data gate)
  Layer 2: Weighted ensemble of LSTM + Prophet + Rules
  Layer 3: SanityChecker + confidence scoring
"""
from __future__ import annotations

import asyncio
from datetime import date, datetime, timedelta
from typing import Optional

import numpy as np
import structlog
import torch

from app.data.pipeline import DataQualityPipeline
from app.domain import (
    AlertSeverity,
    Anomaly,
    CriticalPoint,
    DailyBalance,
    DataQualityScore,
    EnsemblePrediction,
    EnsembleWeights,
    ModelUsed,
    QualityLabel,
    RecurringPattern,
    SanityResult,
    Transaction,
    UncertaintyResult,
)
from app.models.baseline_model import ProphetStyleDecomposer, RuleBasedPredictor
from app.models.lstm_model import TreasuryLSTM
from app.models.model_race import ModelRaceEvaluator, RaceResult
from app.models.timesfm_predictor import TimesFMPredictor

log = structlog.get_logger()


class SanityChecker:
    """
    Last line of defense before returning a prediction to the API.
    Catches LSTM hallucinations and impossible outputs.
    All 5 rules must pass; if any fails → fallback to rules-only.
    """

    def validate(
        self,
        prediction: np.ndarray,
        current_balance: float,
        avg_daily_volume: float = 500.0,
        avg_monthly_income: float = 3000.0,
        avg_monthly_expenses: float = 2500.0,
    ) -> SanityResult:
        failed: list[str] = []
        max_daily_change = max(avg_daily_volume * 5, abs(current_balance) * 0.50)

        # Rule 1 — No infinities
        if np.any(np.isinf(prediction)):
            failed.append("RULE1_INFINITY")

        # Rule 2 — No NaN
        if np.any(np.isnan(prediction)):
            failed.append("RULE2_NAN")

        # Rule 3 — No impossible daily jumps
        if len(prediction) > 1:
            daily_changes = np.abs(np.diff(prediction))
            if np.any(daily_changes >= max_daily_change):
                bad_idx = int(np.argmax(daily_changes))
                failed.append(
                    f"RULE3_CONTINUITY jump={daily_changes[bad_idx]:.0f} at day={bad_idx}"
                )

        # Rule 4 — Reasonable range
        lower_bound = current_balance - 12 * avg_monthly_expenses
        upper_bound = current_balance + 12 * avg_monthly_income
        if np.any(prediction < lower_bound) or np.any(prediction > upper_bound):
            failed.append(
                f"RULE4_RANGE [{prediction.min():.0f}, {prediction.max():.0f}] "
                f"vs [{lower_bound:.0f}, {upper_bound:.0f}]"
            )

        # Rule 5 — J0 continuity
        if len(prediction) > 0:
            j0_diff = abs(prediction[0] - current_balance)
            if j0_diff >= max_daily_change:
                failed.append(f"RULE5_J0 diff={j0_diff:.0f}")

        if failed:
            log.warning("sanity_check_failed", rules=failed, balance=current_balance)

        return SanityResult(
            passed=len(failed) == 0,
            failed_rules=failed,
            sanity_override=len(failed) > 0,
        )


def _mae_to_confidence_label(mae: float, history_days: int, quality: DataQualityScore) -> str:
    if quality.label == QualityLabel.INSUFFICIENT:
        return "INSUFFICIENT"
    if mae < 80 and history_days >= 180:
        return "HIGH"
    if mae < 150:
        return "MEDIUM"
    return "LOW"


def _compute_mae_estimate(history_days: int, quality: DataQualityScore) -> float:
    """Estimate expected MAE based on data quality and history length."""
    if history_days < 30:
        return 450.0
    if history_days < 90:
        return 300.0
    if quality.label == QualityLabel.LOW:
        return 220.0
    if quality.label == QualityLabel.MEDIUM:
        return 160.0
    if history_days >= 180:
        return 90.0
    return 130.0


class EnsemblePredictor:
    """
    Orchestrates the 3-model ensemble.
    Applies dynamic weights by history length and data quality.
    Runs SanityChecker on all outputs before returning.
    """

    def __init__(self) -> None:
        self._lstm: Optional[TreasuryLSTM] = None
        self._prophet = ProphetStyleDecomposer()
        self._rules = RuleBasedPredictor()
        self._pipeline = DataQualityPipeline()
        self._sanity = SanityChecker()
        self._race = ModelRaceEvaluator()   # online model selection
        self._load_lstm()
        self._timesfm = TimesFMPredictor()
        self._timesfm.load()

    def _load_lstm(self) -> None:
        import os
        model_path = os.getenv("LSTM_MODEL_PATH", "models/treasury_lstm.pt")
        if os.path.exists(model_path):
            try:
                checkpoint = torch.load(model_path, map_location="cpu")
                cfg = checkpoint.get("model_config", {})
                self._lstm = TreasuryLSTM(**cfg)
                self._lstm.load_state_dict(checkpoint["model_state_dict"])
                self._lstm.eval()
                log.info("lstm_loaded", path=model_path)
            except Exception as e:
                log.warning("lstm_load_failed", error=str(e))
                self._lstm = None
        else:
            log.info("lstm_not_found", path=model_path)

    def compute_weights(
        self,
        history_days: int,
        quality_score: DataQualityScore,
        lstm_available: bool,
    ) -> EnsembleWeights:
        """
        Dynamic weight table by history length and quality.
        Rule: alerts use p25 (pessimistic), model weights govern the mean.
        """
        if not lstm_available:
            # LSTM not available (OOM, not trained, etc.)
            return EnsembleWeights(lstm=0.0, prophet=0.60, rules=0.40)

        if history_days >= 180:
            w = EnsembleWeights(lstm=0.70, prophet=0.20, rules=0.10)
        elif history_days >= 90:
            w = EnsembleWeights(lstm=0.50, prophet=0.30, rules=0.20)
        elif history_days >= 30:
            w = EnsembleWeights(lstm=0.20, prophet=0.40, rules=0.40)
        else:
            return EnsembleWeights(lstm=0.00, prophet=0.00, rules=1.00)

        # Quality adjustments
        if quality_score.label == QualityLabel.LOW:
            # Shift 0.20 from LSTM to Rules
            shift = min(0.20, w.lstm)
            w = EnsembleWeights(
                lstm=w.lstm - shift,
                prophet=w.prophet,
                rules=w.rules + shift,
            )
        elif quality_score.label == QualityLabel.INSUFFICIENT:
            w = EnsembleWeights(lstm=0.0, prophet=0.0, rules=1.0)

        return w

    def predict(
        self,
        account_id: str,
        transactions: list[Transaction],
        horizon: int = 90,
        user_scaler: Optional[tuple[float, float]] = None,
    ) -> EnsemblePrediction:
        """
        Full ensemble prediction pipeline:
        1. DataQualityPipeline
        2. Compute weights
        3. Run available models
        4. Weighted average
        5. SanityChecker
        6. Build EnsemblePrediction
        """
        import time
        t0 = time.monotonic()

        # ── Model race: resolve past predictions against incoming actuals ───────────
        actual_map: dict[date, float] = {tx.date: tx.balance for tx in transactions}
        self._race.evaluate(account_id, actual_map)
        # Default — overwritten after models have run
        race = RaceResult(winner="blend", confidence=0.0, reason="not_yet_evaluated")

        # Step 1 — Data quality gate
        pipeline_result = self._pipeline.run(transactions, horizon_days=horizon)
        quality = pipeline_result.quality_score
        user_features = pipeline_result.user_features
        history_days = user_features.history_days

        current_balance = 0.0
        if transactions:
            sorted_txs = sorted(transactions, key=lambda t: t.date)
            current_balance = sorted_txs[-1].balance

        # Step 2 — Weights
        lstm_ok = self._lstm is not None and history_days >= 90
        weights = self.compute_weights(history_days, quality, lstm_ok)

        # Step 3 — Run models
        rules_pred: Optional[np.ndarray] = None
        prophet_pred: Optional[np.ndarray] = None
        lstm_uncertainty: Optional[UncertaintyResult] = None
        sanity_override = False
        attention_highlights: list[date] = []

        # Always run rules
        try:
            bp = self._rules.predict(
                current_balance=current_balance,
                recurring_patterns=pipeline_result.recurring_patterns,
                fiscal_features=pipeline_result.fiscal_features,
                recent_transactions=transactions,
                horizon=horizon,
                income_volatility=user_features.income_volatility,
                avg_monthly_income=user_features.avg_monthly_income,
            )
            rules_pred = bp.daily_balances[:horizon]
        except Exception as e:
            log.error("rules_prediction_failed", error=str(e))
            rules_pred = np.full(horizon, current_balance)

        # Prophet (≥ 30d)
        if history_days >= 30 and weights.prophet > 0:
            try:
                self._prophet.fit(pipeline_result.cleaned_series)
                last_tx_date = (
                    max(t.date for t in transactions)
                    if transactions
                    else date.today()
                )
                pp = self._prophet.predict(
                    horizon=horizon,
                    future_fiscal_features=pipeline_result.fiscal_features,
                    start_date=last_tx_date + timedelta(days=1),
                )
                prophet_pred = pp.daily_balances[:horizon]
            except Exception as e:
                log.warning("prophet_prediction_failed", error=str(e))
                prophet_pred = None
                weights = EnsembleWeights(
                    lstm=weights.lstm,
                    prophet=0.0,
                    rules=min(1.0, weights.rules + weights.prophet),
                )

        # LSTM (≥ 90d)
        if lstm_ok and weights.lstm > 0:
            try:
                fm = pipeline_result.feature_matrix
                if fm is not None:
                    x = torch.tensor(fm, dtype=torch.float32).unsqueeze(0)  # (1, seq, 15)
                    lstm_uncertainty = self._lstm.predict_with_uncertainty(
                        x, n_samples=50, horizon=horizon
                    )
                    # Denormalize if user scaler provided
                    if user_scaler:
                        mean_s, std_s = user_scaler
                        lstm_uncertainty = UncertaintyResult(
                            mean_prediction=lstm_uncertainty.mean_prediction * std_s + mean_s,
                            std_prediction=lstm_uncertainty.std_prediction * std_s,
                            p5_prediction=lstm_uncertainty.p5_prediction * std_s + mean_s,
                            p25_prediction=lstm_uncertainty.p25_prediction * std_s + mean_s,
                            p75_prediction=lstm_uncertainty.p75_prediction * std_s + mean_s,
                            p95_prediction=lstm_uncertainty.p95_prediction * std_s + mean_s,
                        )
                else:
                    log.warning("lstm_skipped", reason="no_feature_matrix")
                    lstm_uncertainty = None
                    weights = self.compute_weights(history_days, quality, False)
            except RuntimeError as e:
                if "out of memory" in str(e).lower() or "cuda" in str(e).lower():
                    log.warning("lstm_oom", error=str(e))
                else:
                    log.error("lstm_runtime_error", error=str(e))
                lstm_uncertainty = None
                weights = self.compute_weights(history_days, quality, False)
            except Exception as e:
                log.error("lstm_prediction_failed", error=str(e))
                lstm_uncertainty = None
                weights = self.compute_weights(history_days, quality, False)

        # TimesFM (zero-shot, always available — needs ≥14 days)
        timesfm_pred: Optional[np.ndarray] = None
        timesfm_p25: Optional[np.ndarray] = None
        timesfm_p75: Optional[np.ndarray] = None
        if self._timesfm.is_loaded and len(transactions) >= 14:
            try:
                day_balance: dict[date, float] = {}
                for tx in sorted(transactions, key=lambda t: t.date):
                    day_balance[tx.date] = tx.balance
                tf_dates_sorted = sorted(day_balance)
                tf_balances_list = [day_balance[d] for d in tf_dates_sorted]
                tf_dates_str = [str(d) for d in tf_dates_sorted]
                if len(tf_balances_list) >= 14:
                    tf_result = self._timesfm.predict(
                        account_id=account_id,
                        daily_balances_history=tf_balances_list,
                        dates_history=tf_dates_str,
                        horizon_days=min(horizon, 90),
                    )
                    n = min(horizon, len(tf_result.daily_balances))
                    timesfm_pred = np.array(
                        [d["balance"] for d in tf_result.daily_balances[:n]], dtype=np.float64
                    )
                    timesfm_p25 = np.array(
                        [d["p25"] for d in tf_result.daily_balances[:n]], dtype=np.float64
                    )
                    timesfm_p75 = np.array(
                        [d["p75"] for d in tf_result.daily_balances[:n]], dtype=np.float64
                    )
                    if n < horizon:
                        timesfm_pred = np.concatenate([timesfm_pred, np.full(horizon - n, timesfm_pred[-1])])
                        timesfm_p25 = np.concatenate([timesfm_p25, np.full(horizon - n, timesfm_p25[-1])])
                        timesfm_p75 = np.concatenate([timesfm_p75, np.full(horizon - n, timesfm_p75[-1])])
            except Exception as e:
                log.warning("timesfm_prediction_failed", error=str(e))
                timesfm_pred = None

        # ── Model race: pick winner, override weights if reliable ──────────────────────────────
        available_for_race: list[str] = ["rules"]
        if prophet_pred is not None:
            available_for_race.append("prophet")
        if lstm_uncertainty is not None:
            available_for_race.append("lstm")
        if timesfm_pred is not None:
            available_for_race.append("timesfm")

        race = self._race.get_winner(account_id, available_for_race)

        if race.winner != "blend":
            if race.winner == "lstm" and lstm_uncertainty is not None:
                weights = EnsembleWeights(lstm=1.0, prophet=0.0, rules=0.0)
            elif race.winner == "prophet" and prophet_pred is not None:
                weights = EnsembleWeights(lstm=0.0, prophet=1.0, rules=0.0)
            elif race.winner == "rules":
                weights = EnsembleWeights(lstm=0.0, prophet=0.0, rules=1.0)
            elif race.winner == "timesfm" and timesfm_pred is not None:
                weights = EnsembleWeights(lstm=0.0, prophet=0.0, rules=0.0, timesfm=1.0)

        # Record each model's individual predictions (first 30 days) for next evaluation
        _last_tx = max(t.date for t in transactions) if transactions else date.today()
        _n_rec = min(30, horizon)
        if rules_pred is not None:
            self._race.record(account_id, "rules", {
                _last_tx + timedelta(days=i + 1): float(rules_pred[i])
                for i in range(_n_rec)
            })
        if prophet_pred is not None:
            self._race.record(account_id, "prophet", {
                _last_tx + timedelta(days=i + 1): float(prophet_pred[i])
                for i in range(_n_rec)
            })
        if lstm_uncertainty is not None:
            self._race.record(account_id, "lstm", {
                _last_tx + timedelta(days=i + 1): float(lstm_uncertainty.mean_prediction[i])
                for i in range(_n_rec)
            })
        if timesfm_pred is not None:
            self._race.record(account_id, "timesfm", {
                _last_tx + timedelta(days=i + 1): float(timesfm_pred[i])
                for i in range(_n_rec)
            })

        # Step 4 — Weighted average (mean predictions)
        ensemble_mean = np.zeros(horizon, dtype=np.float64)
        if rules_pred is not None:
            ensemble_mean += weights.rules * rules_pred
        if prophet_pred is not None:
            ensemble_mean += weights.prophet * prophet_pred
        if lstm_uncertainty is not None:
            ensemble_mean += weights.lstm * lstm_uncertainty.mean_prediction[:horizon]
        if timesfm_pred is not None:
            ensemble_mean += weights.timesfm * timesfm_pred

        # p25 for alerts (conservative)
        if lstm_uncertainty is not None:
            p25 = lstm_uncertainty.p25_prediction[:horizon]
            p75 = lstm_uncertainty.p75_prediction[:horizon]
            band_width = float(np.mean(p75 - p25))
        elif timesfm_p25 is not None and weights.timesfm >= 0.9:
            p25 = timesfm_p25[:horizon]
            p75 = timesfm_p75[:horizon]
            band_width = float(np.mean(p75 - p25))
        else:
            margin = user_features.avg_monthly_income * user_features.income_volatility
            p25 = ensemble_mean - margin
            p75 = ensemble_mean + margin
            band_width = float(2 * margin)

        # Step 5 — SanityChecker
        avg_daily_vol = user_features.avg_monthly_income / 30.0 + user_features.avg_monthly_expenses / 30.0
        sanity = self._sanity.validate(
            prediction=ensemble_mean,
            current_balance=current_balance,
            avg_daily_volume=avg_daily_vol,
            avg_monthly_income=user_features.avg_monthly_income,
            avg_monthly_expenses=user_features.avg_monthly_expenses,
        )
        if not sanity.passed:
            log.warning("sanity_override_triggered", rules=sanity.failed_rules)
            rules_fallback = self._rules.predict(
                current_balance=current_balance,
                recurring_patterns=pipeline_result.recurring_patterns,
                fiscal_features=pipeline_result.fiscal_features,
                recent_transactions=transactions,
                horizon=horizon,
                income_volatility=user_features.income_volatility,
                avg_monthly_income=user_features.avg_monthly_income,
            )
            ensemble_mean = rules_fallback.daily_balances[:horizon]
            p25 = rules_fallback.uncertainty_lower[:horizon]
            p75 = rules_fallback.uncertainty_upper[:horizon]
            sanity_override = True
            weights = EnsembleWeights(lstm=0.0, prophet=0.0, rules=1.0)

        # Step 6 — Build output
        # Determine model_used label (race winner takes priority over blend labels)
        if race.winner == "lstm" and weights.lstm >= 0.9:
            model_used = ModelUsed.RACE_LSTM
        elif race.winner == "prophet" and weights.prophet >= 0.9:
            model_used = ModelUsed.RACE_PROPHET
        elif race.winner == "rules" and weights.rules >= 0.9:
            model_used = ModelUsed.RACE_RULES
        elif race.winner == "timesfm" and weights.timesfm >= 0.9:
            model_used = ModelUsed.RACE_TIMESFM
        elif weights.lstm >= 0.5:
            model_used = ModelUsed.LSTM_ENSEMBLE
        elif weights.prophet >= 0.3:
            model_used = ModelUsed.PROPHET_RULES
        elif quality.label == QualityLabel.INSUFFICIENT:
            model_used = ModelUsed.INSUFFICIENT
        else:
            model_used = ModelUsed.RULES_ONLY

        # Daily balances
        last_tx_date = max(t.date for t in transactions) if transactions else date.today()
        daily_balances = [
            DailyBalance(
                date=last_tx_date + timedelta(days=i + 1),
                balance=float(ensemble_mean[i]),
                balance_p25=float(p25[i]),
                balance_p75=float(p75[i]),
            )
            for i in range(horizon)
        ]

        # Summary
        min_idx = int(np.argmin(ensemble_mean))
        min_balance = float(ensemble_mean[min_idx])
        min_date = last_tx_date + timedelta(days=min_idx + 1)
        deficit = min_balance < 0

        # Critical points (based on p25 — conservative)
        critical: list[CriticalPoint] = []
        today = date.today()
        for i, bal in enumerate(p25):
            if bal < 0:
                future_date = last_tx_date + timedelta(days=i + 1)
                days_away = (future_date - today).days
                if days_away <= 7:
                    sev = AlertSeverity.CRITICAL
                elif days_away <= 30:
                    sev = AlertSeverity.HIGH
                else:
                    sev = AlertSeverity.MEDIUM
                critical.append(
                    CriticalPoint(
                        date=future_date,
                        predicted_balance=float(bal),
                        severity=sev,
                        cause="balance_below_zero_p25",
                    )
                )

        # Anomalies from pipeline
        anomalies: list[Anomaly] = []
        if not pipeline_result.cleaned_series.empty and "outlier_type" in pipeline_result.cleaned_series.columns:
            for _, row in pipeline_result.cleaned_series.iterrows():
                if row.get("outlier_type"):
                    anomalies.append(
                        Anomaly(
                            date=row["date"].date() if hasattr(row["date"], "date") else row["date"],
                            amount=float(row.get("amount", 0)),
                            outlier_type=str(row["outlier_type"]),
                            outlier_score=float(row.get("outlier_score", 0.5)),
                        )
                    )

        # Attention highlights (top-3 most attended days)
        if lstm_uncertainty is not None and history_days >= 90 and pipeline_result.feature_matrix is not None:
            try:
                fm = pipeline_result.feature_matrix
                x_tensor = torch.tensor(fm, dtype=torch.float32).unsqueeze(0)
                with torch.no_grad():
                    _, meta = self._lstm.forward(x_tensor, return_attention=True)
                if meta.get("attention_weights") is not None:
                    attn = meta["attention_weights"][0].numpy()
                    top_idx = np.argsort(attn)[-3:][::-1]
                    seq_start = last_tx_date - timedelta(days=len(fm) - 1)
                    attention_highlights = [
                        seq_start + timedelta(days=int(idx)) for idx in top_idx
                    ]
            except Exception:
                pass

        # Confidence & MAE estimate
        mae_estimate = _compute_mae_estimate(history_days, quality)
        if sanity_override:
            confidence_score = 0.30
        elif quality.label == QualityLabel.INSUFFICIENT:
            confidence_score = 0.20
        elif quality.label == QualityLabel.LOW:
            confidence_score = 0.45
        elif quality.label == QualityLabel.MEDIUM:
            confidence_score = 0.65
        else:
            confidence_score = 0.80 + min(0.15, (history_days - 180) / 1000.0)

        # Apply confidence penalty for moderate gaps
        confidence_score = max(
            0.10,
            confidence_score - quality.moderate_gap_count * 0.05 - quality.severe_gap_count * 0.15,
        )
        confidence_label = _mae_to_confidence_label(mae_estimate, history_days, quality)

        elapsed_ms = int((time.monotonic() - t0) * 1000)
        log.info(
            "ensemble_prediction_complete",
            account_id=account_id,
            history_days=history_days,
            model_used=model_used.value,
            confidence_score=round(confidence_score, 3),
            mae_estimate=round(mae_estimate, 1),
            duration_ms=elapsed_ms,
        )

        return EnsemblePrediction(
            account_id=account_id,
            generated_at=datetime.utcnow(),
            horizon_days=horizon,
            daily_balance=daily_balances,
            min_balance=min_balance,
            min_balance_date=min_date,
            predicted_deficit=deficit,
            deficit_amount=abs(min_balance) if deficit else None,
            deficit_date=min_date if deficit else None,
            confidence_score=confidence_score,
            confidence_label=confidence_label,
            mae_estimate=mae_estimate,
            uncertainty_band_width=band_width,
            model_used=model_used,
            history_days=history_days,
            data_quality=quality,
            weights_used=weights,
            attention_highlights=attention_highlights,
            critical_points=critical,
            anomalies_detected=anomalies,
            recurring_detected=pipeline_result.recurring_patterns,
            sanity_override=sanity_override,
            model_race_winner=race.winner if race.winner != "blend" else None,
            model_race_scores={
                k: {
                    "mae_30d": round(v.mae_30d, 1) if v.mae_30d != float("inf") else None,
                    "n_eval_points": v.n_eval_points,
                }
                for k, v in race.scores.items()
            },
        )

    async def predict_async(
        self,
        transactions: list[Transaction],
        horizon: int = 90,
        account_id: str = "",
    ) -> EnsemblePrediction:
        """Async wrapper for use in concurrent prediction tasks."""
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(
            None, self.predict, account_id, transactions, horizon
        )
