"""Feature engineering for the fraud model.

The raw request is turned into a small, fixed vector of derived features. Two
choices matter here:

* **Ratios, not absolutes.** ``amount_z_score`` and ``amount_to_max_ratio``
  describe the transaction *relative to this customer's own behaviour*. A
  50,000 transfer is unremarkable for one customer and a red flag for another,
  so an absolute amount would make the model mostly a wealth detector.
* **Bounded values.** Each feature is clipped, so one extreme input cannot
  dominate the vector and push every other signal into irrelevance.
"""

from __future__ import annotations

from decimal import Decimal

import numpy as np

from app.schemas import ScoringRequest

#: Order is fixed and must match between training and inference.
FEATURE_NAMES: list[str] = [
    "amount_z_score",
    "amount_to_mean_ratio",
    "amount_to_max_ratio",
    "balance_ratio",
    "hour_risk",
    "new_beneficiary",
    "velocity_1h",
    "velocity_24h",
    "payee_spread",
]

#: Minimum history before the statistical features mean anything.
MIN_HISTORY_FOR_STATS = 5

#: Hours considered unusual for retail banking activity (local time).
UNUSUAL_HOURS = set(range(0, 6))


def _as_float(value: Decimal | float | int) -> float:
    return float(value)


def amount_z_score(request: ScoringRequest) -> float:
    """How many standard deviations the amount sits from the customer's mean.

    Returns 0.0 when there is too little history, rather than a large value -
    a new customer is unknown, not suspicious, and treating thin history as
    anomalous would flag every first transaction.
    """
    if request.history_count < MIN_HISTORY_FOR_STATS:
        return 0.0

    mean = _as_float(request.customer_mean_debit)
    std = _as_float(request.customer_std_debit)
    amount = _as_float(request.amount)

    if std <= 0:
        # Identical history: any change is notable, but cap it so a customer
        # with one repeated amount does not get an unbounded score.
        return 0.0 if abs(amount - mean) < 1e-9 else 3.0

    return float(np.clip((amount - mean) / std, -10.0, 10.0))


def amount_to_mean_ratio(request: ScoringRequest) -> float:
    """Amount as a multiple of the customer's average debit."""
    mean = _as_float(request.customer_mean_debit)
    if mean <= 0:
        return 1.0
    return float(np.clip(_as_float(request.amount) / mean, 0.0, 50.0))


def amount_to_max_ratio(request: ScoringRequest) -> float:
    """Amount relative to the largest the customer has ever sent.

    Exceeding a personal record is a meaningfully different signal from simply
    being above average.
    """
    maximum = _as_float(request.customer_max_debit)
    if maximum <= 0:
        return 1.0
    return float(np.clip(_as_float(request.amount) / maximum, 0.0, 20.0))


def hour_risk(request: ScoringRequest) -> float:
    """Whether the attempt falls in the small hours.

    A graded value rather than a flag: 02:00 is more unusual than 05:00.
    """
    hour = request.hour_of_day
    if hour not in UNUSUAL_HOURS:
        return 0.0
    # Peak oddness at 03:00, tapering either side.
    return float(1.0 - abs(hour - 3) / 4.0)


def velocity(count: int, cap: int) -> float:
    """Normalised transaction frequency."""
    return float(np.clip(count / cap, 0.0, 1.0))


def build_vector(request: ScoringRequest) -> np.ndarray:
    """Builds the feature vector for one request.

    Returns a ``(1, len(FEATURE_NAMES))`` array so it can be passed straight to
    scikit-learn without reshaping at the call site.
    """
    values = [
        amount_z_score(request),
        amount_to_mean_ratio(request),
        amount_to_max_ratio(request),
        float(np.clip(_as_float(request.balance_ratio), 0.0, 2.0)),
        hour_risk(request),
        1.0 if request.is_new_beneficiary else 0.0,
        velocity(request.recent_debit_count_1h, 10),
        velocity(request.recent_debit_count_24h, 30),
        velocity(request.distinct_payees_7d, 15),
    ]
    return np.array([values], dtype=np.float64)


def describe(request: ScoringRequest) -> dict[str, float]:
    """Named feature values, for building explanations."""
    vector = build_vector(request)[0]
    return dict(zip(FEATURE_NAMES, vector.tolist(), strict=True))
