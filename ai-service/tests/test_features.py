"""Unit tests for feature engineering."""

from __future__ import annotations

import pytest

from app.features import (
    FEATURE_NAMES,
    amount_to_max_ratio,
    amount_to_mean_ratio,
    amount_z_score,
    build_vector,
    hour_risk,
)
from app.schemas import ScoringRequest


def request_with(**overrides) -> ScoringRequest:
    base = {
        "amount": "2500.00",
        "customerMeanDebit": "2400.00",
        "customerStdDebit": "600.00",
        "customerMaxDebit": "8000.00",
        "historyCount": 60,
        "hourOfDay": 14,
        "isNewBeneficiary": False,
        "beneficiaryAgeHours": 2200,
        "recentDebitCount1h": 0,
        "recentDebitCount24h": 2,
        "distinctPayees7d": 3,
        "balanceRatio": "0.05",
        "category": "FOOD",
    }
    base.update(overrides)
    return ScoringRequest(**base)


class TestAmountZScore:
    def test_amount_at_the_mean_scores_zero(self):
        request = request_with(amount="2400.00")
        assert amount_z_score(request) == pytest.approx(0.0, abs=0.01)

    def test_amount_above_the_mean_is_positive(self):
        # 6000 against a mean of 2400 and sd of 600 is six sigma.
        request = request_with(amount="6000.00")
        assert amount_z_score(request) == pytest.approx(6.0, abs=0.01)

    def test_thin_history_returns_zero_rather_than_flagging(self):
        """A new customer is unknown, not suspicious.

        Without this, every first transaction would look anomalous.
        """
        request = request_with(amount="99999.00", historyCount=2)
        assert amount_z_score(request) == 0.0

    def test_zero_variance_history_is_bounded(self):
        request = request_with(amount="9000.00", customerStdDebit="0.00")
        assert amount_z_score(request) == 3.0

    def test_zero_variance_matching_amount_scores_zero(self):
        request = request_with(amount="2400.00", customerStdDebit="0.00")
        assert amount_z_score(request) == 0.0

    def test_z_score_is_clipped(self):
        request = request_with(amount="100000000.00")
        assert amount_z_score(request) == 10.0


class TestRatios:
    def test_mean_ratio(self):
        request = request_with(amount="4800.00")
        assert amount_to_mean_ratio(request) == pytest.approx(2.0)

    def test_mean_ratio_with_no_history_defaults_to_one(self):
        request = request_with(customerMeanDebit="0.00")
        assert amount_to_mean_ratio(request) == 1.0

    def test_max_ratio_detects_exceeding_a_personal_record(self):
        request = request_with(amount="16000.00")
        assert amount_to_max_ratio(request) == pytest.approx(2.0)

    def test_max_ratio_with_no_history_defaults_to_one(self):
        request = request_with(customerMaxDebit="0.00")
        assert amount_to_max_ratio(request) == 1.0


class TestHourRisk:
    @pytest.mark.parametrize("hour", [6, 9, 12, 15, 18, 21, 23])
    def test_daytime_hours_carry_no_risk(self, hour):
        assert hour_risk(request_with(hourOfDay=hour)) == 0.0

    def test_three_am_is_the_peak(self):
        assert hour_risk(request_with(hourOfDay=3)) == pytest.approx(1.0)

    def test_risk_tapers_away_from_three_am(self):
        at_three = hour_risk(request_with(hourOfDay=3))
        at_five = hour_risk(request_with(hourOfDay=5))
        assert at_five < at_three
        assert at_five > 0.0


class TestVector:
    def test_vector_shape_matches_feature_names(self):
        vector = build_vector(request_with())
        assert vector.shape == (1, len(FEATURE_NAMES))

    def test_all_features_are_finite(self):
        import numpy as np

        extreme = request_with(
            amount="999999999.00",
            customerMeanDebit="0.01",
            customerStdDebit="0.00",
            customerMaxDebit="0.01",
            balanceRatio="99.0",
            recentDebitCount1h=10000,
            recentDebitCount24h=10000,
            distinctPayees7d=10000,
        )
        vector = build_vector(extreme)
        assert np.all(np.isfinite(vector)), "no feature may be NaN or infinite"

    def test_velocity_features_are_normalised(self):
        vector = build_vector(request_with(
            recentDebitCount1h=500, recentDebitCount24h=500, distinctPayees7d=500,
        ))[0]
        # Indices 6, 7, 8 are the velocity features.
        assert vector[6] == 1.0
        assert vector[7] == 1.0
        assert vector[8] == 1.0
