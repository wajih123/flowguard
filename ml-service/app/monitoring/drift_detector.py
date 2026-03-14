"""
Drift Detector — PSI (Population Stability Index) + ADWIN concept drift.

Two drift types:
  1. Data drift  — feature distribution shift (PSI + KS test)
  2. Concept drift — model accuracy degradation (rolling MAE vs baseline)

Rules:
  PSI < 0.10 → NO_DRIFT
  PSI 0.10-0.20 → MODERATE_DRIFT
  PSI > 0.20 → SIGNIFICANT_DRIFT
  PSI > 0.25 → CRITICAL_DRIFT (trigger retrain)
  rolling_mae_7d / baseline_mae > 1.5 → HIGH_ALERT
  rolling_mae_7d / baseline_mae > 2.0 → trigger_retrain = True
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from typing import Optional

import numpy as np
from scipy import stats

from app.db import append_quality_log, get_baseline_mae, get_recent_actuals, record_prediction_actual
from app.domain import ConceptDriftResult, DriftResult

log = logging.getLogger(__name__)


def _psi_bins(
    expected: np.ndarray,
    actual: np.ndarray,
    n_bins: int = 10,
    eps: float = 1e-6,
) -> float:
    """
    Population Stability Index.
    Bins are defined by expected distribution quantiles.
    PSI = Σ (actual_pct - expected_pct) × ln(actual_pct / expected_pct)
    """
    breakpoints = np.nanpercentile(expected, np.linspace(0, 100, n_bins + 1))
    # Deduplicate breakpoints
    breakpoints = np.unique(breakpoints)
    if len(breakpoints) < 2:
        return 0.0

    expected_hist, _ = np.histogram(expected, bins=breakpoints)
    actual_hist, _ = np.histogram(actual, bins=breakpoints)

    expected_pct = (expected_hist + eps) / (expected_hist.sum() + eps * len(expected_hist))
    actual_pct = (actual_hist + eps) / (actual_hist.sum() + eps * len(actual_hist))

    psi = np.sum((actual_pct - expected_pct) * np.log(actual_pct / expected_pct))
    return float(psi)


def _drift_label(psi: float) -> str:
    if psi < 0.10:
        return "NO_DRIFT"
    if psi < 0.20:
        return "MODERATE_DRIFT"
    if psi < 0.25:
        return "SIGNIFICANT_DRIFT"
    return "CRITICAL_DRIFT"


class DriftDetector:
    """
    Detects two types of model drift:
    - Data drift (feature distribution shift)
    - Concept drift (prediction accuracy degradation)
    """

    def __init__(self, n_bins: int = 10, ks_significance: float = 0.05) -> None:
        self.n_bins = n_bins
        self.ks_significance = ks_significance

        # ADWIN parameters (simplified exponential window)
        self._adwin_window: list[float] = []
        self._adwin_max_size: int = 200

    # -------------------------------------------------------------------------
    # Data drift (PSI per feature + KS test)
    # -------------------------------------------------------------------------

    def detect_data_drift(
        self,
        reference_features: np.ndarray,   # (N_ref, n_features)
        current_features: np.ndarray,      # (N_cur, n_features)
        feature_names: Optional[list[str]] = None,
    ) -> DriftResult:
        """
        Compute PSI for each feature plus a global KS test on the first feature.
        Returns worst-case PSI and overall drift label.
        """
        if reference_features.shape[0] < 10 or current_features.shape[0] < 10:
            return DriftResult(
                psi_score=0.0,
                drift_label="INSUFFICIENT_DATA",
                ks_statistic=0.0,
                ks_p_value=1.0,
                feature_psi={},
                trigger_retrain=False,
                detected_at=datetime.utcnow(),
            )

        n_features = min(reference_features.shape[1], current_features.shape[1])
        feature_psi: dict[str, float] = {}
        psi_values: list[float] = []

        for i in range(n_features):
            name = feature_names[i] if feature_names and i < len(feature_names) else f"f{i}"
            psi = _psi_bins(reference_features[:, i], current_features[:, i], self.n_bins)
            feature_psi[name] = round(psi, 4)
            psi_values.append(psi)

        global_psi = float(np.mean(psi_values))
        worst_psi = float(np.max(psi_values))

        # KS test on first feature (daily_balance)
        ks_stat, ks_p = stats.ks_2samp(
            reference_features[:, 0].flatten(),
            current_features[:, 0].flatten(),
        )

        label = _drift_label(worst_psi)
        trigger = worst_psi > 0.25 or (ks_p < self.ks_significance and worst_psi > 0.15)

        log.info(
            "data_drift_detected",
            worst_psi=round(worst_psi, 4),
            global_psi=round(global_psi, 4),
            label=label,
            trigger_retrain=trigger,
        )

        return DriftResult(
            psi_score=worst_psi,
            drift_label=label,
            ks_statistic=float(ks_stat),
            ks_p_value=float(ks_p),
            feature_psi=feature_psi,
            trigger_retrain=trigger,
            detected_at=datetime.utcnow(),
        )

    # -------------------------------------------------------------------------
    # Concept drift (rolling MAE vs baseline)
    # -------------------------------------------------------------------------

    def detect_concept_drift(
        self,
        account_id: Optional[str] = None,
        days_window_7: int = 7,
        days_window_30: int = 30,
    ) -> ConceptDriftResult:
        """
        Compare rolling MAE (7-day and 30-day windows) vs. baseline MAE
        stored in the active model version.
        """
        actuals = get_recent_actuals(days=max(days_window_30, 30))
        baseline_mae = get_baseline_mae()

        if not actuals or baseline_mae is None or baseline_mae <= 0:
            return ConceptDriftResult(
                rolling_mae_7d=None,
                rolling_mae_30d=None,
                baseline_mae=baseline_mae,
                drift_ratio_7d=None,
                drift_ratio_30d=None,
                drift_label="INSUFFICIENT_DATA",
                trigger_retrain=False,
                detected_at=datetime.utcnow(),
            )

        # Compute absolute errors for each actual
        now = datetime.utcnow()
        cutoff_7 = now - timedelta(days=days_window_7)
        cutoff_30 = now - timedelta(days=days_window_30)

        errors_7: list[float] = []
        errors_30: list[float] = []
        for record in actuals:
            if record.get("actual_balance") is None or record.get("predicted_balance") is None:
                continue
            err = abs(float(record["actual_balance"]) - float(record["predicted_balance"]))
            recorded_at = record.get("recorded_at", now)
            if isinstance(recorded_at, str):
                recorded_at = datetime.fromisoformat(recorded_at)
            if recorded_at >= cutoff_7:
                errors_7.append(err)
            if recorded_at >= cutoff_30:
                errors_30.append(err)

        mae_7d = float(np.mean(errors_7)) if errors_7 else None
        mae_30d = float(np.mean(errors_30)) if errors_30 else None

        ratio_7 = (mae_7d / baseline_mae) if mae_7d is not None else None
        ratio_30 = (mae_30d / baseline_mae) if mae_30d is not None else None

        # ADWIN update (simplified: track 7-day ratios)
        if ratio_7 is not None:
            self._adwin_window.append(ratio_7)
            if len(self._adwin_window) > self._adwin_max_size:
                self._adwin_window.pop(0)

        # Determine drift
        trigger = False
        if ratio_7 is not None and ratio_7 > 2.0:
            label = "CRITICAL_CONCEPT_DRIFT"
            trigger = True
        elif ratio_7 is not None and ratio_7 > 1.5:
            label = "HIGH_CONCEPT_DRIFT"
        elif ratio_30 is not None and ratio_30 > 1.3:
            label = "MODERATE_CONCEPT_DRIFT"
        else:
            label = "NO_CONCEPT_DRIFT"

        log.info(
            "concept_drift_check",
            mae_7d=mae_7d,
            mae_30d=mae_30d,
            baseline_mae=baseline_mae,
            ratio_7d=ratio_7,
            label=label,
            trigger_retrain=trigger,
        )

        return ConceptDriftResult(
            rolling_mae_7d=mae_7d,
            rolling_mae_30d=mae_30d,
            baseline_mae=baseline_mae,
            drift_ratio_7d=ratio_7,
            drift_ratio_30d=ratio_30,
            drift_label=label,
            trigger_retrain=trigger,
            detected_at=datetime.utcnow(),
        )

    # -------------------------------------------------------------------------
    # Feedback loop — record actuals on each Nordigen sync
    # -------------------------------------------------------------------------

    def record_actuals(
        self,
        account_id: str,
        prediction_date: date,
        predicted_balance: float,
        actual_balance: float,
    ) -> None:
        """
        Called by the Nordigen sync webhook/scheduler to close the feedback loop.
        Stores in prediction_actuals table for concept drift detection.
        """
        record_prediction_actual(
            account_id=account_id,
            prediction_date=prediction_date,
            predicted_balance=predicted_balance,
            actual_balance=actual_balance,
        )
        error = abs(actual_balance - predicted_balance)
        append_quality_log(account_id=account_id, mae_error=error)
        log.debug(
            "actual_recorded",
            account_id=account_id,
            date=prediction_date.isoformat(),
            error=round(error, 2),
        )
