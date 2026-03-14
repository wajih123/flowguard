"""
FlowGuard — Data Quality Pipeline (7 étapes séquentielles).

Appelé systématiquement AVANT tout modèle de prédiction.
Ne laisse jamais passer des données invalides au modèle.
"""
from __future__ import annotations

import hashlib
from datetime import date, timedelta
from typing import Optional

import holidays
import numpy as np
import pandas as pd
import structlog
from scipy import stats as scipy_stats
from sklearn.ensemble import IsolationForest

from app.domain import (
    DataQualityScore,
    GapReport,
    GapSeverity,
    PipelineResult,
    QualityLabel,
    RecurringCategory,
    RecurringFrequency,
    RecurringPattern,
    Transaction,
    UserFeatureVector,
    UserProfile,
)

log = structlog.get_logger()

# French holidays for 2026-2031
_FR_HOLIDAYS = holidays.France(years=range(2026, 2032))

# Fiscal dates (month, day)
_URSSAF_DATES = [(2, 15), (5, 15), (8, 15), (11, 15)]
_TVA_DAY = 24
_IR_DATES = [(9, 15), (10, 15)]


class DataQualityPipeline:
    """
    7-step data quality pipeline.
    Steps must all run in order via run().
    """

    # ── Step 1 — Gap detection ─────────────────────────────────────────────────

    def detect_gaps(
        self, series: pd.DataFrame, max_gap_days: int = 3
    ) -> list[GapReport]:
        """
        Detect date ranges with missing data in the time series.
        Returns list of GapReport classified by severity.
        """
        if series.empty or "date" not in series.columns:
            return []

        dates = pd.to_datetime(series["date"]).dt.date.sort_values()
        if len(dates) < 2:
            return []

        gaps: list[GapReport] = []
        all_dates = set(dates)

        current = dates.iloc[0]
        end_date = dates.iloc[-1]

        while current <= end_date:
            if current not in all_dates:
                gap_start = current
                gap_end = current
                while gap_end + timedelta(days=1) <= end_date and (
                    gap_end + timedelta(days=1)
                ) not in all_dates:
                    gap_end += timedelta(days=1)

                gap_days = (gap_end - gap_start).days + 1

                if gap_days <= 3:
                    severity = GapSeverity.MINOR
                elif gap_days <= 14:
                    severity = GapSeverity.MODERATE
                else:
                    severity = GapSeverity.SEVERE

                gaps.append(
                    GapReport(
                        start=gap_start,
                        end=gap_end,
                        days=gap_days,
                        severity=severity,
                    )
                )
                current = gap_end + timedelta(days=1)
            else:
                current += timedelta(days=1)

        log.info("gaps_detected", count=len(gaps), severe=sum(1 for g in gaps if g.severity == GapSeverity.SEVERE))
        return gaps

    # ── Step 2 — Gap imputation ────────────────────────────────────────────────

    def impute_gaps(
        self, series: pd.DataFrame, gaps: list[GapReport]
    ) -> pd.DataFrame:
        """
        Fill gaps by severity:
          MINOR    → zero transactions, maintain balance
          MODERATE → linear interpolation on balance
          SEVERE   → do NOT impute; cut series to post-gap only
        """
        if not gaps or series.empty:
            return series.copy()

        # Separate severe gaps — we cut the series at the last severe gap end
        severe_gaps = [g for g in gaps if g.severity == GapSeverity.SEVERE]
        if severe_gaps:
            latest_severe = max(severe_gaps, key=lambda g: g.end)
            # Use only data after the severe gap
            series = series[pd.to_datetime(series["date"]).dt.date > latest_severe.end].copy()
            log.warning(
                "severe_gap_series_cut",
                cut_after=str(latest_severe.end),
                remaining_rows=len(series),
            )

        # Rebuild full date range
        if series.empty:
            return series

        min_date = pd.to_datetime(series["date"]).min()
        max_date = pd.to_datetime(series["date"]).max()
        full_range = pd.date_range(min_date, max_date, freq="D")
        series = series.copy()
        series["date"] = pd.to_datetime(series["date"])
        series = series.set_index("date").reindex(full_range)
        series.index.name = "date"

        # Fill missing amounts with 0 (no transaction)
        if "amount" in series.columns:
            series["amount"] = series["amount"].fillna(0)

        # For balance: linear interpolation for MODERATE, ffill for MINOR
        if "balance" in series.columns:
            series["balance"] = series["balance"].interpolate(method="linear")
            series["balance"] = series["balance"].ffill().bfill()

        series = series.reset_index()

        # Mark imputed rows
        gap_dates = set()
        for g in gaps:
            if g.severity != GapSeverity.SEVERE:
                d = g.start
                while d <= g.end:
                    gap_dates.add(d)
                    d += timedelta(days=1)

        series["imputed"] = series["date"].dt.date.isin(gap_dates)
        return series

    # ── Step 3 — Outlier detection ─────────────────────────────────────────────

    def detect_outliers(
        self, series: pd.DataFrame, method: str = "iqr"
    ) -> pd.DataFrame:
        """
        Tags outliers — does NOT remove them.
        Financial transactions can have legitimately large values.
        Uses 3.0× IQR (not 1.5×) to avoid flagging real transactions.
        """
        series = series.copy()
        series["outlier_type"] = None
        series["outlier_score"] = 0.0
        series["is_duplicate"] = False

        if "amount" not in series.columns or len(series) < 4:
            return series

        amounts = series["amount"].dropna()
        if len(amounts) < 4:
            return series

        if method == "iqr":
            q1, q3 = np.percentile(amounts, [25, 75])
            iqr = q3 - q1
            lower = q1 - 3.0 * iqr
            upper = q3 + 3.0 * iqr

            for idx, row in series.iterrows():
                amt = row.get("amount", 0)
                if pd.isna(amt):
                    continue
                if amt > upper:
                    score = min((amt - upper) / (upper - q3 + 1e-9), 1.0)
                    series.at[idx, "outlier_type"] = "high_income"
                    series.at[idx, "outlier_score"] = float(score)
                elif amt < lower:
                    score = min((lower - amt) / (q3 - q1 + 1e-9), 1.0)
                    series.at[idx, "outlier_type"] = "high_expense"
                    series.at[idx, "outlier_score"] = float(score)

        elif method == "zscore":
            mean_amt = amounts.mean()
            std_amt = amounts.std() + 1e-9
            for idx, row in series.iterrows():
                amt = row.get("amount", 0)
                if pd.isna(amt):
                    continue
                z = abs((amt - mean_amt) / std_amt)
                if z > 3.0:
                    series.at[idx, "outlier_type"] = "high_income" if amt > 0 else "high_expense"
                    series.at[idx, "outlier_score"] = float(min(z / 10.0, 1.0))

        elif method == "isolation_forest":
            iso = IsolationForest(contamination=0.05, random_state=42)
            X = amounts.values.reshape(-1, 1)
            preds = iso.fit_predict(X)
            scores = iso.score_samples(X)
            for i, (idx, row) in enumerate(series.iterrows()):
                if preds[i] == -1:
                    amt = row.get("amount", 0)
                    series.at[idx, "outlier_type"] = "high_income" if amt > 0 else "high_expense"
                    series.at[idx, "outlier_score"] = float(min(-scores[i], 1.0))

        # Duplicate detection: same amount ±0.01, same creditor, within 24h
        if "creditor_debtor" in series.columns:
            for idx, row in series.iterrows():
                if row.get("is_duplicate"):
                    continue
                amt = row.get("amount", None)
                creditor = row.get("creditor_debtor", "")
                row_date = row.get("date")
                if amt is None or pd.isna(amt):
                    continue
                mask = (
                    (series.index != idx)
                    & (series["amount"].sub(amt).abs() <= 0.01)
                    & (series.get("creditor_debtor", "") == creditor)
                    & (~series.get("is_duplicate", pd.Series(False, index=series.index)))
                )
                if "date" in series.columns and row_date is not None:
                    mask &= (series["date"] - row_date).abs() <= pd.Timedelta(hours=24)
                if mask.any():
                    series.at[idx, "is_duplicate"] = True
                    series.at[idx, "outlier_type"] = "duplicate"

        return series

    # ── Step 4 — Recurring pattern detection ──────────────────────────────────

    def detect_recurring(
        self, transactions: list[Transaction]
    ) -> list[RecurringPattern]:
        """
        Identify recurring transactions by frequency analysis.
        Groups by creditor name or amount bucket (±5%).
        """
        if not transactions:
            return []

        # Group by creditor
        from collections import defaultdict

        groups: dict[str, list[Transaction]] = defaultdict(list)
        for tx in transactions:
            key = (tx.creditor_debtor or "").strip().lower()
            if not key:
                # Fall back to amount bucket (round to nearest 5%)
                amt_bucket = round(abs(tx.amount) * 20) / 20
                key = f"__amount_{amt_bucket:.2f}"
            groups[key].append(tx)

        patterns: list[RecurringPattern] = []

        for creditor, txs in groups.items():
            if len(txs) < 2:
                continue

            txs_sorted = sorted(txs, key=lambda t: t.date)
            intervals = [
                (txs_sorted[i].date - txs_sorted[i - 1].date).days
                for i in range(1, len(txs_sorted))
            ]
            if not intervals:
                continue

            median_interval = float(np.median(intervals))
            std_interval = float(np.std(intervals)) if len(intervals) > 1 else 0.0
            median_amount = float(np.median([abs(t.amount) for t in txs]))

            if std_interval < 3.0:
                frequency = RecurringFrequency.HIGHLY_RECURRING
            elif std_interval < 7.0:
                frequency = RecurringFrequency.RECURRING
            else:
                frequency = RecurringFrequency.NON_RECURRING

            if frequency == RecurringFrequency.NON_RECURRING:
                continue

            # Round interval to standard periods
            for standard in [7, 14, 30, 90, 365]:
                if abs(median_interval - standard) <= 5:
                    median_interval = float(standard)
                    break

            confidence = max(0.0, 1.0 - std_interval / max(median_interval, 1.0))
            last_tx = txs_sorted[-1]
            next_occ = last_tx.date + timedelta(days=int(median_interval))

            # Category detection
            category = self._detect_category(
                creditor, median_amount, txs_sorted
            )

            patterns.append(
                RecurringPattern(
                    creditor=creditor if not creditor.startswith("__amount_") else f"montant_{median_amount:.0f}€",
                    median_amount=median_amount,
                    interval_days=int(median_interval),
                    std_dev_days=std_interval,
                    confidence=confidence,
                    next_occurrence=next_occ,
                    category=category,
                    frequency=frequency,
                )
            )

        log.info("recurring_detected", count=len(patterns))
        return patterns

    def _detect_category(
        self,
        creditor: str,
        median_amount: float,
        txs: list[Transaction],
    ) -> RecurringCategory:
        cred_lower = creditor.lower()

        # URSSAF detection: French social contributions
        urssaf_dates = {(2, 15), (5, 15), (8, 15), (11, 15)}
        if 200 <= median_amount <= 5000:
            near_urssaf = any(
                (t.date.month, t.date.day) in urssaf_dates
                or abs(t.date.day - 15) <= 3
                and t.date.month in {2, 5, 8, 11}
                for t in txs
            )
            if near_urssaf:
                return RecurringCategory.URSSAF

        # TVA detection
        dgfip_keywords = ["dgfip", "tresor public", "trésor public", "impots", "impôts"]
        if any(kw in cred_lower for kw in dgfip_keywords):
            if 50 <= median_amount <= 50000:
                return RecurringCategory.TVA

        # Generic category mapping
        if any(kw in cred_lower for kw in ["loyer", "rent", "habitatio"]):
            return RecurringCategory.LOYER
        if any(kw in cred_lower for kw in ["netflix", "spotify", "amazon", "sfr", "orange", "free", "bouygues"]):
            return RecurringCategory.ABONNEMENT
        if any(kw in cred_lower for kw in ["mutuelle", "health", "harmonie", "mgen"]):
            return RecurringCategory.MUTUELLE
        if any(kw in cred_lower for kw in ["assurance", "maif", "axa", "allianz", "groupama"]):
            return RecurringCategory.ASSURANCE
        if any(kw in cred_lower for kw in ["edf", "engie", "electricite", "électricité", "gaz"]):
            return RecurringCategory.ENERGIE

        return RecurringCategory.AUTRE

    # ── Step 5 — Fiscal feature injection ─────────────────────────────────────

    def inject_fiscal_features(
        self, prediction_horizon: pd.DatetimeIndex
    ) -> pd.DataFrame:
        """
        Build a DataFrame of binary fiscal event features
        for the prediction horizon window.
        """
        rows = []
        for dt in prediction_horizon:
            d = dt.date()
            rows.append(
                {
                    "date": d,
                    "urssaf_due_in_7d": int(self._days_to_next_urssaf(d) <= 7),
                    "urssaf_due_in_30d": int(self._days_to_next_urssaf(d) <= 30),
                    "tva_due_in_7d": int(self._days_to_next_tva(d) <= 7),
                    "tva_due_in_30d": int(self._days_to_next_tva(d) <= 30),
                    "ir_due_in_30d": int(self._days_to_next_ir(d) <= 30),
                    "month_end_proximity": self._month_end_proximity(d),
                    "quarter_end": int(self._is_quarter_end(d)),
                    "is_weekend": int(d.weekday() >= 5),
                    "is_french_holiday": int(d in _FR_HOLIDAYS),
                }
            )
        return pd.DataFrame(rows)

    def _days_to_next_urssaf(self, d: date) -> int:
        min_days = 999
        for year in [d.year, d.year + 1]:
            for month, day in _URSSAF_DATES:
                try:
                    target = date(year, month, day)
                    diff = (target - d).days
                    if diff >= 0:
                        min_days = min(min_days, diff)
                except ValueError:
                    pass
        return min_days

    def _days_to_next_tva(self, d: date) -> int:
        # TVA due on the 24th of each month
        for delta in range(0, 40):
            candidate = d + timedelta(days=delta)
            if candidate.day == _TVA_DAY:
                return (candidate - d).days
        return 999

    def _days_to_next_ir(self, d: date) -> int:
        min_days = 999
        for year in [d.year, d.year + 1]:
            for month, day in _IR_DATES:
                try:
                    target = date(year, month, day)
                    diff = (target - d).days
                    if diff >= 0:
                        min_days = min(min_days, diff)
                except ValueError:
                    pass
        return min_days

    def _month_end_proximity(self, d: date) -> float:
        import calendar
        last_day = calendar.monthrange(d.year, d.month)[1]
        days_remaining = last_day - d.day
        return max(0.0, 1.0 - days_remaining / 7.0) if days_remaining <= 7 else 0.0

    def _is_quarter_end(self, d: date) -> bool:
        import calendar
        last_day = calendar.monthrange(d.year, d.month)[1]
        return d.month in {3, 6, 9, 12} and (last_day - d.day) <= 15

    # ── Step 6 — User feature extraction ──────────────────────────────────────

    def extract_user_features(
        self,
        transactions: list[Transaction],
        recurring: list[RecurringPattern],
    ) -> UserFeatureVector:
        """
        Build a static user profile vector from transaction history.
        Used for clustering and model selection.
        """
        if not transactions:
            return UserFeatureVector(
                avg_monthly_income=0.0,
                income_volatility=1.0,
                avg_monthly_expenses=0.0,
                expense_regularity=0.0,
                recurring_expense_ratio=0.0,
                estimated_profile=UserProfile.UNKNOWN,
                history_days=0,
                data_quality_score=0.0,
            )

        txs_sorted = sorted(transactions, key=lambda t: t.date)
        history_days = (txs_sorted[-1].date - txs_sorted[0].date).days + 1

        # Monthly income and expenses
        monthly: dict[str, dict[str, float]] = {}
        for tx in transactions:
            key = f"{tx.date.year}-{tx.date.month:02d}"
            if key not in monthly:
                monthly[key] = {"income": 0.0, "expense": 0.0}
            if tx.amount > 0:
                monthly[key]["income"] += tx.amount
            else:
                monthly[key]["expense"] += abs(tx.amount)

        months = list(monthly.values())
        incomes = np.array([m["income"] for m in months]) if months else np.array([0.0])
        expenses = np.array([m["expense"] for m in months]) if months else np.array([0.0])

        avg_monthly_income = float(incomes.mean())
        avg_monthly_expenses = float(expenses.mean())
        income_volatility = float(incomes.std() / max(incomes.mean(), 1.0))

        # Expense regularity: uniformity of expenses across months
        if expenses.std() < 1e-9 or expenses.mean() < 1e-9:
            expense_regularity = 1.0
        else:
            cv = expenses.std() / expenses.mean()
            expense_regularity = float(max(0.0, 1.0 - cv))

        # Recurring expense ratio
        total_expenses = float(expenses.sum())
        recurring_expenses = sum(
            p.median_amount
            for p in recurring
            if p.median_amount > 0 and p.category != RecurringCategory.AUTRE
        )
        recurring_expense_ratio = (
            recurring_expenses / max(total_expenses, 1.0)
        )

        # Profile detection
        profile = self._estimate_profile(income_volatility, incomes, recurring)

        return UserFeatureVector(
            avg_monthly_income=avg_monthly_income,
            income_volatility=income_volatility,
            avg_monthly_expenses=avg_monthly_expenses,
            expense_regularity=expense_regularity,
            recurring_expense_ratio=min(recurring_expense_ratio, 1.0),
            estimated_profile=profile,
            history_days=history_days,
            data_quality_score=0.0,  # filled in by compute_quality_score
        )

    def _estimate_profile(
        self,
        income_volatility: float,
        incomes: np.ndarray,
        recurring: list[RecurringPattern],
    ) -> UserProfile:
        has_tva = any(p.category == RecurringCategory.TVA for p in recurring)
        has_urssaf = any(p.category == RecurringCategory.URSSAF for p in recurring)

        # Check for multiple income sources
        if len(incomes) > 3 and income_volatility > 0.3:
            unique_patterns = len({p.creditor for p in recurring if p.median_amount > 500})
            if unique_patterns >= 2:
                return UserProfile.MIXED

        if income_volatility < 0.1 and len(incomes) >= 2:
            # Regular monthly income interval → salaried
            return UserProfile.SALARIED

        if income_volatility > 0.3 and has_tva:
            return UserProfile.ARTISAN

        if income_volatility > 0.4 or has_urssaf:
            return UserProfile.FREELANCE

        return UserProfile.UNKNOWN

    # ── Step 7 — Quality score ─────────────────────────────────────────────────

    def compute_quality_score(
        self,
        gaps: list[GapReport],
        outliers_ratio: float,
        history_days: int,
    ) -> DataQualityScore:
        """
        Compute an aggregate quality score 0.0–1.0.
        Used to weight prediction confidence.
        """
        base_score = 1.0
        severe_count = sum(1 for g in gaps if g.severity == GapSeverity.SEVERE)
        moderate_count = sum(1 for g in gaps if g.severity == GapSeverity.MODERATE)

        base_score -= severe_count * 0.15
        base_score -= moderate_count * 0.05

        if outliers_ratio > 0.10:
            base_score -= 0.10
        if history_days < 30:
            base_score -= 0.40
        elif history_days < 60:
            base_score -= 0.20
        elif history_days < 90:
            base_score -= 0.10

        score = max(0.0, base_score)

        if score > 0.8:
            label = QualityLabel.HIGH
        elif score > 0.5:
            label = QualityLabel.MEDIUM
        elif score > 0.2:
            label = QualityLabel.LOW
        else:
            label = QualityLabel.INSUFFICIENT

        return DataQualityScore(
            score=score,
            label=label,
            severe_gap_count=severe_count,
            moderate_gap_count=moderate_count,
            outliers_ratio=outliers_ratio,
            history_days=history_days,
            severe_gap_flag=severe_count > 0,
        )

    # ── Feature matrix for LSTM ────────────────────────────────────────────────

    def build_feature_matrix(
        self,
        transactions: list[Transaction],
        recurring: list[RecurringPattern],
        fiscal: pd.DataFrame,
        user_features: UserFeatureVector,
        end_date: date,
        seq_len: int = 90,
    ) -> np.ndarray:
        """
        Build the 15-feature daily matrix for LSTM input.
        Returns array of shape (seq_len, 15).
        Feature layout:
          0  daily_balance (norm)      8  tva_due_in_30d
          1  daily_net_flow            9  is_weekend
          2  is_recurring_outflow      10 is_french_holiday
          3  recurring_amount          11 month_end_proximity
          4  days_since_last_income    12 quarter_end
          5  urssaf_due_in_7d          13 income_volatility
          6  urssaf_due_in_30d         14 avg_monthly_expenses (norm)
          7  tva_due_in_7d
        """
        start_date = end_date - timedelta(days=seq_len - 1)
        date_range = [start_date + timedelta(days=i) for i in range(seq_len)]

        # Build daily transaction lookups
        tx_by_date: dict[date, list[Transaction]] = {}
        for tx in transactions:
            tx_by_date.setdefault(tx.date, []).append(tx)

        # Recurring outflow dates
        recurring_outflows: dict[date, float] = {}
        for p in recurring:
            if p.median_amount > 0:
                d = p.next_occurrence
                while d >= start_date:
                    d -= timedelta(days=p.interval_days)
                d += timedelta(days=p.interval_days)
                while d <= end_date:
                    if start_date <= d <= end_date:
                        recurring_outflows[d] = recurring_outflows.get(d, 0.0) + p.median_amount
                    d += timedelta(days=p.interval_days)

        fiscal_idx = {row["date"]: row for _, row in fiscal.iterrows()} if not fiscal.empty else {}

        # Build initial balance estimate from transactions
        if transactions:
            sorted_txs = sorted(transactions, key=lambda t: t.date)
            current_balance = sorted_txs[0].balance if sorted_txs[0].balance else 0.0
        else:
            current_balance = 0.0

        scale_mean = user_features.avg_monthly_income or 1.0

        features = np.zeros((seq_len, 15), dtype=np.float32)
        days_since_last_income = 0

        for i, d in enumerate(date_range):
            txs = tx_by_date.get(d, [])
            net_flow = sum(t.amount for t in txs)
            if txs:
                current_balance = txs[-1].balance if txs[-1].balance else current_balance + net_flow

            fi = fiscal_idx.get(d, {})

            is_rec_outflow = 1.0 if d in recurring_outflows else 0.0
            rec_amt = recurring_outflows.get(d, 0.0) / max(scale_mean, 1.0)

            income_today = sum(t.amount for t in txs if t.amount > 0)
            if income_today > 0:
                days_since_last_income = 0
            else:
                days_since_last_income += 1

            features[i, 0]  = current_balance / max(scale_mean, 1.0)
            features[i, 1]  = net_flow / max(scale_mean, 1.0)
            features[i, 2]  = is_rec_outflow
            features[i, 3]  = rec_amt
            features[i, 4]  = min(days_since_last_income / 90.0, 1.0)
            features[i, 5]  = float(fi.get("urssaf_due_in_7d", 0))
            features[i, 6]  = float(fi.get("urssaf_due_in_30d", 0))
            features[i, 7]  = float(fi.get("tva_due_in_7d", 0))
            features[i, 8]  = float(fi.get("tva_due_in_30d", 0))
            features[i, 9]  = float(fi.get("is_weekend", int(d.weekday() >= 5)))
            features[i, 10] = float(fi.get("is_french_holiday", int(d in _FR_HOLIDAYS)))
            features[i, 11] = float(fi.get("month_end_proximity", 0.0))
            features[i, 12] = float(fi.get("quarter_end", 0))
            features[i, 13] = user_features.income_volatility
            features[i, 14] = user_features.avg_monthly_expenses / max(scale_mean, 1.0)

        return features

    # ── Main run ───────────────────────────────────────────────────────────────

    def run(
        self,
        transactions: list[Transaction],
        horizon_days: int = 90,
    ) -> PipelineResult:
        """
        Execute all 7 pipeline steps in order.
        Returns PipelineResult with cleaned data and all metadata.
        """
        if not transactions:
            empty_quality = DataQualityScore(
                score=0.0,
                label=QualityLabel.INSUFFICIENT,
                severe_gap_count=0,
                moderate_gap_count=0,
                outliers_ratio=0.0,
                history_days=0,
            )
            empty_features = UserFeatureVector(
                avg_monthly_income=0.0,
                income_volatility=1.0,
                avg_monthly_expenses=0.0,
                expense_regularity=0.0,
                recurring_expense_ratio=0.0,
                estimated_profile=UserProfile.UNKNOWN,
                history_days=0,
                data_quality_score=0.0,
            )
            empty_weights = EnsembleWeights_placeholder()
            return PipelineResult(
                cleaned_series=pd.DataFrame(),
                raw_series=pd.DataFrame(),
                gaps=[],
                recurring_patterns=[],
                fiscal_features=pd.DataFrame(),
                user_features=empty_features,
                quality_score=empty_quality,
                transactions=[],
            )

        # Build raw DataFrame
        raw_df = pd.DataFrame(
            [
                {
                    "date": t.date,
                    "amount": t.amount,
                    "balance": t.balance,
                    "label": t.label,
                    "creditor_debtor": t.creditor_debtor or "",
                }
                for t in transactions
            ]
        )
        raw_df["date"] = pd.to_datetime(raw_df["date"])

        # Step 1 — detect_gaps
        gaps = self.detect_gaps(raw_df)

        # Step 2 — impute_gaps
        cleaned = self.impute_gaps(raw_df, gaps)

        # Step 3 — detect_outliers
        cleaned = self.detect_outliers(cleaned)
        outlier_count = int(cleaned["outlier_type"].notna().sum()) if "outlier_type" in cleaned.columns else 0
        outliers_ratio = outlier_count / max(len(cleaned), 1)

        # Step 4 — detect_recurring
        recurring = self.detect_recurring(transactions)

        # Step 5 — inject fiscal features for prediction horizon
        last_date = max(t.date for t in transactions)
        horizon_index = pd.date_range(
            start=last_date + timedelta(days=1), periods=horizon_days, freq="D"
        )
        fiscal = self.inject_fiscal_features(horizon_index)

        # Historical fiscal features (for feature matrix)
        history_days_val = (last_date - min(t.date for t in transactions)).days + 1
        hist_index = pd.date_range(
            end=last_date, periods=min(history_days_val, 90), freq="D"
        )
        fiscal_hist = self.inject_fiscal_features(hist_index)

        # Step 6 — extract user features
        user_features = self.extract_user_features(transactions, recurring)

        # Step 7 — compute quality score
        quality_score = self.compute_quality_score(gaps, outliers_ratio, history_days_val)
        user_features.data_quality_score = quality_score.score

        # Build LSTM feature matrix
        feature_matrix = None
        if history_days_val >= 30:
            try:
                feature_matrix = self.build_feature_matrix(
                    transactions=transactions,
                    recurring=recurring,
                    fiscal=fiscal_hist,
                    user_features=user_features,
                    end_date=last_date,
                    seq_len=min(history_days_val, 90),
                )
            except Exception as e:
                log.warning("feature_matrix_build_failed", error=str(e))

        log.info(
            "pipeline_complete",
            history_days=history_days_val,
            quality_label=quality_score.label.value,
            gaps=len(gaps),
            recurring=len(recurring),
        )

        return PipelineResult(
            cleaned_series=cleaned,
            raw_series=raw_df,
            gaps=gaps,
            recurring_patterns=recurring,
            fiscal_features=fiscal,
            user_features=user_features,
            quality_score=quality_score,
            transactions=transactions,
            feature_matrix=feature_matrix,
        )


class EnsembleWeights_placeholder:
    """Used only for empty pipeline result."""
    lstm = 0.0
    prophet = 0.0
    rules = 1.0
