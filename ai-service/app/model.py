"""The fraud detection model: an Isolation Forest plus an explainable rule layer.

Why this design
---------------
An Isolation Forest is well suited to this problem: fraud labels are scarce and
unbalanced, whereas *normal* behaviour is abundant, and unsupervised anomaly
detection learns the shape of normal without needing labelled fraud. What it
cannot do is explain itself - it yields a single path-length score with no
per-feature attribution.

The specification is explicit that alerts must be explainable and that a bare
score must never be presented as AI. So the score is a blend:

* The **model layer** detects multivariate anomalies: combinations of features
  that are individually unremarkable but jointly unusual. This is the part a
  rule set genuinely cannot do.
* The **rule layer** contributes score points with a plain-language reason
  attached to each one. This is what the customer and the analyst read.

Both contributions are reported separately in the response, so nobody has to
take the blend on trust.

Limitations, documented
-----------------------
The forest is trained on *synthetic* behaviour generated below, not on real
fraud. It therefore learns "unusual relative to a plausible population", not
"fraudulent". It would need retraining on real labelled outcomes before any
production use, and the false-positive feedback the application collects is
stored for exactly that purpose rather than being applied automatically.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from app.features import FEATURE_NAMES, build_vector, describe
from app.schemas import RiskFactor, ScoringRequest

LOGGER = logging.getLogger(__name__)

MODEL_VERSION = "isolation_forest_v1"
MODEL_PATH = Path(__file__).parent.parent / "models" / "fraud_model.joblib"

#: Synthetic population size. Large enough for a stable contamination
#: boundary, small enough to train in under a second on CI.
TRAINING_SAMPLES = 4000

#: Expected share of anomalies in the synthetic population.
CONTAMINATION = 0.06

#: Deterministic seed so the same input always scores the same. A model that
#: scored differently between restarts would be impossible to demonstrate or
#: to investigate after the fact.
RANDOM_SEED = 20261102


@dataclass
class RuleHit:
    """One rule that fired, with the points it contributed."""

    feature: str
    value: str
    points: int
    explanation: str


def generate_training_data(samples: int = TRAINING_SAMPLES,
                           seed: int = RANDOM_SEED) -> np.ndarray:
    """Generates a synthetic population of banking behaviour.

    The bulk is ordinary activity: amounts near the customer's own mean, daytime
    hours, established payees, low velocity. A ``CONTAMINATION`` share is drawn
    from deliberately odd regions of the space - large multiples of the mean,
    small-hours activity, new payees, bursts of transactions.

    This is how the forest learns a boundary. It is synthetic, and that
    limitation is stated in the module docstring and in the API response's
    model version.
    """
    rng = np.random.default_rng(seed)
    normal_count = int(samples * (1 - CONTAMINATION))
    anomaly_count = samples - normal_count

    # --- ordinary behaviour -------------------------------------------------
    normal = np.column_stack([
        rng.normal(0.0, 0.8, normal_count),                      # amount_z_score
        np.abs(rng.normal(1.0, 0.35, normal_count)),             # amount_to_mean_ratio
        np.abs(rng.normal(0.45, 0.2, normal_count)),             # amount_to_max_ratio
        np.abs(rng.normal(0.12, 0.1, normal_count)),             # balance_ratio
        np.zeros(normal_count),                                  # hour_risk
        rng.binomial(1, 0.04, normal_count).astype(float),       # new_beneficiary
        np.abs(rng.normal(0.08, 0.06, normal_count)),            # velocity_1h
        np.abs(rng.normal(0.15, 0.1, normal_count)),             # velocity_24h
        np.abs(rng.normal(0.2, 0.12, normal_count)),             # payee_spread
    ])

    # --- anomalous behaviour ------------------------------------------------
    anomalies = np.column_stack([
        rng.normal(5.5, 2.2, anomaly_count),                     # far from the mean
        np.abs(rng.normal(9.0, 4.0, anomaly_count)),             # large multiple
        np.abs(rng.normal(3.2, 1.4, anomaly_count)),             # beyond personal max
        np.abs(rng.normal(0.8, 0.25, anomaly_count)),            # draining the balance
        rng.uniform(0.3, 1.0, anomaly_count),                    # small hours
        rng.binomial(1, 0.75, anomaly_count).astype(float),      # fresh payee
        np.abs(rng.normal(0.65, 0.25, anomaly_count)),           # rapid succession
        np.abs(rng.normal(0.7, 0.2, anomaly_count)),             # high daily count
        np.abs(rng.normal(0.72, 0.2, anomaly_count)),            # many payees
    ])

    data = np.vstack([normal, anomalies])
    rng.shuffle(data)
    return np.clip(data, -10.0, 50.0)


class FraudModel:
    """Trains, persists and applies the fraud model."""

    def __init__(self) -> None:
        self._forest: IsolationForest | None = None
        self._scaler: StandardScaler | None = None
        self._training_samples = 0

    # -- lifecycle ------------------------------------------------------

    @property
    def trained(self) -> bool:
        return self._forest is not None and self._scaler is not None

    @property
    def training_samples(self) -> int:
        return self._training_samples

    def train(self, samples: int = TRAINING_SAMPLES) -> None:
        """Fits the scaler and the forest on synthetic data."""
        data = generate_training_data(samples)

        self._scaler = StandardScaler()
        scaled = self._scaler.fit_transform(data)

        self._forest = IsolationForest(
            n_estimators=200,
            contamination=CONTAMINATION,
            max_samples="auto",
            random_state=RANDOM_SEED,
            n_jobs=1,
        )
        self._forest.fit(scaled)
        self._training_samples = len(data)
        LOGGER.info("Trained %s on %d synthetic samples", MODEL_VERSION, len(data))

    def ensure_trained(self) -> None:
        """Loads a persisted model, or trains and persists one."""
        if self.trained:
            return
        if MODEL_PATH.exists():
            try:
                import joblib

                payload = joblib.load(MODEL_PATH)
                self._forest = payload["forest"]
                self._scaler = payload["scaler"]
                self._training_samples = payload.get("samples", 0)
                LOGGER.info("Loaded model from %s", MODEL_PATH)
                return
            except (OSError, KeyError, ValueError) as exc:
                LOGGER.warning("Could not load persisted model (%s), retraining", exc)

        self.train()
        self.save()

    def save(self) -> None:
        """Persists the fitted model so a restart does not retrain."""
        if not self.trained:
            return
        try:
            import joblib

            MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
            joblib.dump(
                {
                    "forest": self._forest,
                    "scaler": self._scaler,
                    "samples": self._training_samples,
                    "version": MODEL_VERSION,
                    "features": FEATURE_NAMES,
                },
                MODEL_PATH,
            )
        except OSError as exc:
            # Persistence is a convenience; failing to write it must not stop
            # the service from scoring.
            LOGGER.warning("Could not persist model: %s", exc)

    # -- scoring --------------------------------------------------------

    def anomaly_score(self, request: ScoringRequest) -> float:
        """Raw decision-function output from the forest.

        Negative values are more anomalous in scikit-learn's convention.
        """
        self.ensure_trained()
        assert self._forest is not None and self._scaler is not None
        vector = self._scaler.transform(build_vector(request))
        return float(self._forest.decision_function(vector)[0])

    def model_points(self, anomaly: float) -> int:
        """Maps the forest's output onto 0-55 score points.

        The decision function is roughly bounded in [-0.3, 0.3] for this
        feature space. The mapping is linear within that window and clamped
        outside it, which keeps the model layer from ever single-handedly
        reaching a CRITICAL score - that requires rule agreement too.
        """
        normalised = (0.15 - anomaly) / 0.35
        return int(np.clip(normalised * 55.0, 0.0, 55.0))

    def score(self, request: ScoringRequest) -> tuple[int, int, int, float, list[RiskFactor]]:
        """Scores a request.

        Returns the blended score, the rule contribution, the model
        contribution, the raw anomaly value and the explanation factors.
        """
        anomaly = self.anomaly_score(request)
        model_points = self.model_points(anomaly)

        hits = evaluate_rules(request)
        rule_points = min(sum(hit.points for hit in hits), 60)

        # The blend takes the larger of the two layers plus a fraction of the
        # other. Either layer alone can raise an alert; agreement raises it
        # further. Summing them outright would double-count the same signal,
        # since the rules and the forest read the same features.
        blended = int(min(
            100,
            max(rule_points, model_points) + 0.4 * min(rule_points, model_points),
        ))

        factors = [
            RiskFactor(
                feature=hit.feature,
                value=hit.value,
                contribution=hit.points,
                explanation=hit.explanation,
            )
            for hit in hits
        ]
        factors.append(
            RiskFactor(
                feature="anomaly_model",
                value=f"{anomaly:.4f}",
                contribution=model_points,
                explanation=(
                    "The Isolation Forest compared this transaction's full feature "
                    "combination against a population of ordinary behaviour. "
                    + (
                        "It sits inside the normal region."
                        if model_points < 20
                        else "It sits toward the edge of the normal region."
                        if model_points < 40
                        else "It sits well outside the normal region."
                    )
                ),
            )
        )
        return blended, rule_points, model_points, anomaly, factors


def evaluate_rules(request: ScoringRequest) -> list[RuleHit]:
    """The explainable rule layer.

    Each rule contributes points and a sentence a human can read. These are
    what make an alert defensible: "amount is 8.4x your usual transfer" is
    actionable, whereas "risk score 72" is not.
    """
    features = describe(request)
    hits: list[RuleHit] = []

    # --- amount deviation --------------------------------------------------
    z = features["amount_z_score"]
    if z >= 4.0:
        hits.append(RuleHit(
            "amount_deviation", f"{z:.1f} standard deviations", 30,
            f"The amount is {z:.1f} standard deviations above your usual transfer size.",
        ))
    elif z >= 2.5:
        hits.append(RuleHit(
            "amount_deviation", f"{z:.1f} standard deviations", 18,
            f"The amount is {z:.1f} standard deviations above your usual transfer size.",
        ))

    ratio = features["amount_to_mean_ratio"]
    if ratio >= 5.0 and request.history_count >= 5:
        hits.append(RuleHit(
            "amount_multiple", f"{ratio:.1f}x average", 15,
            f"This is {ratio:.1f} times your average transaction amount.",
        ))

    max_ratio = features["amount_to_max_ratio"]
    if max_ratio > 1.5 and request.history_count >= 5:
        hits.append(RuleHit(
            "exceeds_personal_maximum", f"{max_ratio:.1f}x previous largest", 14,
            f"This is {max_ratio:.1f} times the largest amount you have sent before.",
        ))

    # --- balance impact ----------------------------------------------------
    balance_ratio = float(request.balance_ratio)
    if balance_ratio >= 0.9:
        hits.append(RuleHit(
            "balance_drain", f"{balance_ratio * 100:.0f}% of available balance", 16,
            f"This would move {balance_ratio * 100:.0f}% of your available balance "
            "in a single transaction.",
        ))
    elif balance_ratio >= 0.6:
        hits.append(RuleHit(
            "balance_drain", f"{balance_ratio * 100:.0f}% of available balance", 8,
            f"This would move {balance_ratio * 100:.0f}% of your available balance.",
        ))

    # --- payee risk --------------------------------------------------------
    if request.is_new_beneficiary:
        hours = request.beneficiary_age_hours
        hits.append(RuleHit(
            "new_beneficiary", f"verified {hours}h ago", 20 if hours < 2 else 12,
            f"The payee was verified only {hours} hour(s) ago. Newly added payees "
            "are a common feature of account takeover.",
        ))

    # --- timing ------------------------------------------------------------
    if features["hour_risk"] > 0:
        hits.append(RuleHit(
            "unusual_hour", f"{request.hour_of_day:02d}:00", 10,
            f"The transaction was attempted at {request.hour_of_day:02d}:00, "
            "outside typical banking hours.",
        ))

    # --- velocity ----------------------------------------------------------
    if request.recent_debit_count_1h >= 5:
        hits.append(RuleHit(
            "rapid_transactions", f"{request.recent_debit_count_1h} in the last hour", 22,
            f"{request.recent_debit_count_1h} transactions have been attempted in the "
            "last hour, which is a pattern seen in automated draining of an account.",
        ))
    elif request.recent_debit_count_1h >= 3:
        hits.append(RuleHit(
            "rapid_transactions", f"{request.recent_debit_count_1h} in the last hour", 10,
            f"{request.recent_debit_count_1h} transactions in the last hour is faster "
            "than your usual pattern.",
        ))

    if request.distinct_payees_7d >= 8:
        hits.append(RuleHit(
            "payee_spread", f"{request.distinct_payees_7d} payees this week", 12,
            f"Money has gone to {request.distinct_payees_7d} different payees in the "
            "last week, which is a wider spread than usual.",
        ))

    # --- thin history ------------------------------------------------------
    if request.history_count < 5:
        hits.append(RuleHit(
            "limited_history", f"{request.history_count} prior transactions", 5,
            f"There are only {request.history_count} prior transactions to compare "
            "against, so this assessment is based on limited history.",
        ))

    return hits


def primary_reason(score: int, factors: list[RiskFactor]) -> str:
    """Picks the headline sentence for an alert.

    The largest non-model contribution wins, because a rule reads better to a
    human than the forest's output. When no rule fired, the model's own
    sentence is used.
    """
    rule_factors = [f for f in factors if f.feature != "anomaly_model"]
    if rule_factors:
        strongest = max(rule_factors, key=lambda f: f.contribution)
        return strongest.explanation

    if score < 40:
        return "This transaction matches your normal pattern."
    return next(
        (f.explanation for f in factors if f.feature == "anomaly_model"),
        "Scored by the anomaly detection model.",
    )
