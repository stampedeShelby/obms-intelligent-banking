"""Unit tests for the fraud model and its rule layer."""

from __future__ import annotations

from app.model import (
    CONTAMINATION,
    FEATURE_NAMES,
    FraudModel,
    evaluate_rules,
    generate_training_data,
    primary_reason,
)
from app.schemas import ScoringRequest


class TestTrainingData:
    def test_shape_matches_the_feature_space(self):
        data = generate_training_data(samples=500)
        assert data.shape == (500, len(FEATURE_NAMES))

    def test_generation_is_deterministic(self):
        """The same seed must give the same population.

        A model that retrained differently on each restart could not be
        demonstrated or investigated after the fact.
        """
        import numpy as np

        first = generate_training_data(samples=300)
        second = generate_training_data(samples=300)
        assert np.array_equal(first, second)

    def test_contains_both_normal_and_anomalous_regions(self):
        data = generate_training_data(samples=2000)
        # The amount_z_score column should span ordinary and extreme values.
        z_column = data[:, 0]
        assert z_column.min() < 1.0
        assert z_column.max() > 4.0

    def test_values_are_bounded(self):
        import numpy as np

        data = generate_training_data(samples=1000)
        assert np.all(np.isfinite(data))
        assert data.max() <= 50.0
        assert data.min() >= -10.0


class TestRuleLayer:
    def test_routine_transaction_fires_few_rules(self, routine_request):
        hits = evaluate_rules(routine_request)
        total = sum(hit.points for hit in hits)
        assert total < 20, "ordinary behaviour should not accumulate rule points"

    def test_suspicious_transaction_fires_several_rules(self, suspicious_request):
        hits = evaluate_rules(suspicious_request)
        features = {hit.feature for hit in hits}

        assert "amount_deviation" in features
        assert "new_beneficiary" in features
        assert "unusual_hour" in features
        assert "rapid_transactions" in features
        assert "balance_drain" in features

    def test_every_rule_carries_a_readable_explanation(self, suspicious_request):
        hits = evaluate_rules(suspicious_request)
        assert hits
        for hit in hits:
            assert hit.explanation
            assert len(hit.explanation) > 20, "explanations must be sentences, not labels"
            assert hit.points > 0

    def test_thin_history_is_flagged_as_a_caveat_not_a_risk(self):
        request = ScoringRequest(amount="500.00", historyCount=1)
        hits = evaluate_rules(request)
        history_hits = [h for h in hits if h.feature == "limited_history"]

        assert len(history_hits) == 1
        assert history_hits[0].points <= 5, (
            "thin history should note a caveat, not drive the score"
        )

    def test_amount_rules_do_not_fire_without_history(self):
        """A large first transaction must not be scored against statistics
        that do not exist yet."""
        request = ScoringRequest(
            amount="500000.00", customerMeanDebit="0.00", historyCount=0,
        )
        features = {hit.feature for hit in evaluate_rules(request)}
        assert "amount_multiple" not in features
        assert "exceeds_personal_maximum" not in features

    def test_new_beneficiary_scores_higher_when_fresher(self):
        just_added = ScoringRequest(
            amount="5000.00", isNewBeneficiary=True, beneficiaryAgeHours=1,
            historyCount=50, customerMeanDebit="4000.00", customerStdDebit="1000.00",
        )
        added_yesterday = ScoringRequest(
            amount="5000.00", isNewBeneficiary=True, beneficiaryAgeHours=20,
            historyCount=50, customerMeanDebit="4000.00", customerStdDebit="1000.00",
        )

        fresh_points = next(h.points for h in evaluate_rules(just_added)
                            if h.feature == "new_beneficiary")
        older_points = next(h.points for h in evaluate_rules(added_yesterday)
                            if h.feature == "new_beneficiary")
        assert fresh_points > older_points


class TestScoring:
    def test_routine_transaction_scores_low(self, model, routine_request):
        blended, _, _, _, _ = model.score(routine_request)
        assert blended < 40, f"ordinary behaviour scored {blended}, expected the LOW band"

    def test_suspicious_transaction_scores_high(self, model, suspicious_request):
        blended, _, _, _, _ = model.score(suspicious_request)
        assert blended >= 65, f"clearly suspicious behaviour scored only {blended}"

    def test_suspicious_scores_above_routine(self, model, routine_request, suspicious_request):
        routine, _, _, _, _ = model.score(routine_request)
        suspicious, _, _, _, _ = model.score(suspicious_request)
        assert suspicious > routine

    def test_score_is_always_in_range(self, model):
        extremes = [
            ScoringRequest(amount="0.00"),
            ScoringRequest(amount="999999999.00", balanceRatio="50.0",
                           recentDebitCount1h=9999, distinctPayees7d=9999),
            ScoringRequest(amount="1.00", historyCount=0),
        ]
        for request in extremes:
            blended, _, _, _, _ = model.score(request)
            assert 0 <= blended <= 100

    def test_scoring_is_deterministic(self, model, suspicious_request):
        """The same input must always give the same score.

        An investigator re-running an assessment has to see what the customer
        saw.
        """
        first, _, _, _, _ = model.score(suspicious_request)
        second, _, _, _, _ = model.score(suspicious_request)
        assert first == second

    def test_response_separates_the_two_layers(self, model, suspicious_request):
        blended, rule_points, model_points, anomaly, _ = model.score(suspicious_request)
        assert rule_points > 0
        assert model_points >= 0
        assert isinstance(anomaly, float)
        # The blend never exceeds the sum of its parts.
        assert blended <= rule_points + model_points

    def test_factors_always_include_the_model_layer(self, model, routine_request):
        _, _, _, _, factors = model.score(routine_request)
        assert any(f.feature == "anomaly_model" for f in factors), (
            "the model's own contribution must always be shown, "
            "even when no rule fired"
        )

    def test_every_factor_has_an_explanation(self, model, suspicious_request):
        _, _, _, _, factors = model.score(suspicious_request)
        for factor in factors:
            assert factor.explanation
            assert factor.feature
            assert factor.value

    def test_model_points_are_capped_below_critical(self, model):
        """The model layer alone cannot reach a CRITICAL score.

        Reaching the top band requires the rule layer to agree, which is what
        keeps an unexplainable score from blocking a customer's money.
        """
        assert model.model_points(-10.0) <= 55
        assert model.model_points(10.0) == 0


class TestPrimaryReason:
    def test_prefers_a_rule_explanation(self, model, suspicious_request):
        blended, _, _, _, factors = model.score(suspicious_request)
        reason = primary_reason(blended, factors)

        assert reason
        assert "Isolation Forest" not in reason, (
            "a human-facing headline should quote a rule, not the algorithm"
        )

    def test_falls_back_to_the_model_when_no_rule_fired(self):
        from app.schemas import RiskFactor

        factors = [RiskFactor(
            feature="anomaly_model", value="-0.2", contribution=50,
            explanation="It sits well outside the normal region.",
        )]
        assert primary_reason(70, factors) == "It sits well outside the normal region."

    def test_low_score_with_no_rules_reports_normality(self):
        assert "normal pattern" in primary_reason(10, [])


class TestLifecycle:
    def test_untrained_model_reports_itself_untrained(self):
        fresh = FraudModel()
        assert fresh.trained is False
        assert fresh.training_samples == 0

    def test_training_marks_the_model_ready(self):
        fresh = FraudModel()
        fresh.train(samples=400)
        assert fresh.trained is True
        assert fresh.training_samples == 400

    def test_contamination_is_a_sensible_share(self):
        assert 0.0 < CONTAMINATION < 0.2
