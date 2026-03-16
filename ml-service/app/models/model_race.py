"""
FlowGuard — ModelRaceEvaluator

On every predict() call the system:
  1. Evaluates past per-model predictions against the newly available actual balances.
  2. Maintains a rolling 30-day MAE per model (in memory; survives the request lifetime).
  3. Returns the "winner" — the model with lowest rolling MAE — for the next prediction.

Strategy: Winner Takes All.
  • < MIN_EVAL_POINTS resolved predictions  → blend (safe default)
  • ≥ MIN_EVAL_POINTS and one model clearly leads → that model alone
  • Weights go back to standard blend when both models are tied (confidence < 0.05)

Thread-safety: the evaluator is request-scoped per account_id key, and
FastAPI runs in a single thread pool for CPU-bound work, so a simple dict is safe.
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from datetime import date, timedelta

import numpy as np
import structlog

log = structlog.get_logger()

# ── Constants ─────────────────────────────────────────────────────────────────
ROLLING_WINDOW_DAYS = 30       # How many days back to compute rolling MAE
MIN_EVAL_POINTS = 7            # Minimum resolved predictions before a winner is declared
MIN_CONFIDENCE = 0.05          # Below this gap ratio → keep blending


# ── Value objects ─────────────────────────────────────────────────────────────

@dataclass
class ModelScore:
    model_name: str
    mae_30d: float          # Rolling 30-day mean absolute error (€)
    n_eval_points: int      # Number of resolved prediction points in window
    is_reliable: bool       # True if n_eval_points >= MIN_EVAL_POINTS


@dataclass
class RaceResult:
    winner: str             # "lstm" | "prophet" | "rules" | "blend"
    scores: dict[str, ModelScore] = field(default_factory=dict)
    confidence: float = 0.0  # Relative gap between winner and runner-up (0–1)
    reason: str = "not_evaluated"


# ── Evaluator ─────────────────────────────────────────────────────────────────

class ModelRaceEvaluator:
    """
    Per-account, per-model rolling MAE tracker.

    Typical usage inside EnsemblePredictor.predict():

        # 1. At the very top of predict() — resolve past predictions
        actual_map = {tx.date: tx.balance for tx in transactions}
        self._race.evaluate(account_id, actual_map)

        # 2. After running all models — find winner
        race = self._race.get_winner(account_id, available_models)

        # 3. Override ensemble weights based on winner
        if race.winner != "blend":
            ...

        # 4. After computing individual model outputs — store for next time
        self._race.record(account_id, "rules", {date: balance, ...})
        self._race.record(account_id, "prophet", ...)
        self._race.record(account_id, "lstm", ...)
    """

    def __init__(self) -> None:
        # {account_id: {model_name: {target_date: predicted_balance}}}
        self._pending: dict[str, dict[str, dict[date, float]]] = defaultdict(
            lambda: defaultdict(dict)
        )
        # {account_id: {model_name: [(target_date, abs_error)]}}
        self._errors: dict[str, dict[str, list[tuple[date, float]]]] = defaultdict(
            lambda: defaultdict(list)
        )

    # ── Public API ────────────────────────────────────────────────────────────

    def record(
        self,
        account_id: str,
        model_name: str,
        predictions: dict[date, float],
    ) -> None:
        """
        Store a model's daily predictions for future evaluation.
        Call once per model after each predict() run (first 30 future days).
        """
        self._pending[account_id][model_name].update(predictions)

    def evaluate(
        self,
        account_id: str,
        actual_balances: dict[date, float],
    ) -> None:
        """
        Cross off pending predictions against actual balances.
        Must be called at the START of every predict() request, before get_winner().
        """
        cutoff = date.today() - timedelta(days=ROLLING_WINDOW_DAYS)

        for model_name, pending in self._pending[account_id].items():
            resolved: list[date] = []
            for target_date, predicted in pending.copy().items():
                if target_date in actual_balances:
                    error = abs(predicted - actual_balances[target_date])
                    self._errors[account_id][model_name].append((target_date, error))
                    resolved.append(target_date)

            for d in resolved:
                del pending[d]

            # Trim to rolling window
            self._errors[account_id][model_name] = [
                (d, e) for d, e in self._errors[account_id][model_name]
                if d >= cutoff
            ]

        if any(self._errors[account_id].values()):
            log.debug(
                "model_race_evaluated",
                account_id=account_id,
                points={m: len(e) for m, e in self._errors[account_id].items()},
            )

    def get_winner(
        self,
        account_id: str,
        available_models: list[str],
    ) -> RaceResult:
        """
        Return the model with the lowest rolling 30-day MAE.

        Returns winner="blend" when:
          - any model has < MIN_EVAL_POINTS resolved predictions, OR
          - the leading MAE gap is too small to be meaningful (< MIN_CONFIDENCE)
        """
        scores: dict[str, ModelScore] = {}

        for model_name in available_models:
            errors = self._errors[account_id].get(model_name, [])
            n = len(errors)
            mae = float(np.mean([e for _, e in errors])) if errors else float("inf")
            scores[model_name] = ModelScore(
                model_name=model_name,
                mae_30d=mae,
                n_eval_points=n,
                is_reliable=n >= MIN_EVAL_POINTS,
            )

        reliable = {k: v for k, v in scores.items() if v.is_reliable}

        if not reliable:
            total = sum(v.n_eval_points for v in scores.values())
            return RaceResult(
                winner="blend",
                scores=scores,
                confidence=0.0,
                reason=f"insufficient_data ({total} pts collected, need {MIN_EVAL_POINTS}+ per model)",
            )

        winner_name = min(reliable, key=lambda k: reliable[k].mae_30d)
        winner = reliable[winner_name]

        # Confidence = relative MAE gap between winner and closest rival
        others = [v.mae_30d for k, v in reliable.items() if k != winner_name]
        if others:
            gap = min(others) - winner.mae_30d
            confidence = min(1.0, gap / max(winner.mae_30d, 50.0))
        else:
            confidence = 0.8  # Only one reliable model — give it high confidence

        if confidence < MIN_CONFIDENCE:
            return RaceResult(
                winner="blend",
                scores=scores,
                confidence=confidence,
                reason=f"tie — gap too small ({confidence:.3f} < {MIN_CONFIDENCE})",
            )

        log.info(
            "model_race_winner_selected",
            account_id=account_id,
            winner=winner_name,
            mae_30d=round(winner.mae_30d, 1),
            n_points=winner.n_eval_points,
            confidence=round(confidence, 2),
        )

        return RaceResult(
            winner=winner_name,
            scores=scores,
            confidence=confidence,
            reason=f"mae_30d={winner.mae_30d:.1f}€ over {winner.n_eval_points} pts",
        )

    def get_score_summary(self, account_id: str) -> dict[str, dict]:
        """Diagnostic — current per-model MAE scores for an account (used in /health)."""
        return {
            model: {
                "mae_30d": round(float(np.mean([e for _, e in errs])), 1) if errs else None,
                "n_eval_points": len(errs),
                "is_reliable": len(errs) >= MIN_EVAL_POINTS,
            }
            for model, errs in self._errors[account_id].items()
        }
