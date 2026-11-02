package com.obms.service.fraud;

import com.obms.domain.Enums;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of one fraud evaluation, as the banking layer consumes it.
 *
 * @param score         0-100, higher means more anomalous
 * @param level         risk band derived from the score
 * @param decision      action the workflow should take
 * @param primaryReason short headline shown to the customer or analyst
 * @param factors       per-feature contributions behind the score
 * @param modelVersion  identifier of the model that produced the score
 * @param degraded      true when the AI service was unreachable and a
 *                      conservative local fallback produced this result
 */
public record RiskAssessment(
        int score,
        Enums.RiskLevel level,
        Decision decision,
        String primaryReason,
        List<RiskFactor> factors,
        String modelVersion,
        boolean degraded
) {

    /** What the transfer workflow does with a scored transaction. */
    public enum Decision {
        /** Proceed without extra friction. */
        ALLOW,
        /** Require an OTP before proceeding. */
        STEP_UP,
        /** Hold for analyst review; funds are not moved. */
        HOLD,
        /** Refuse outright in this simulation and flag for investigation. */
        BLOCK
    }

    /**
     * One explainable contribution to the score.
     *
     * @param feature      feature name, e.g. {@code amount_vs_mean}
     * @param value        the observed value
     * @param contribution how many score points this feature added
     * @param explanation  plain-language reason a human can read
     */
    public record RiskFactor(String feature, String value, int contribution, String explanation) {
    }

    /**
     * Conservative result used when the AI service cannot be reached.
     *
     * <p>Failing open on every transaction would make the control useless, and
     * failing closed on every transaction would take the bank offline whenever
     * the model is down. The compromise: small transfers proceed, and anything
     * large enough to matter gets a step-up challenge the customer can still
     * complete.</p>
     */
    public static RiskAssessment degradedFallback(BigDecimal amount, BigDecimal stepUpThreshold) {
        boolean large = amount.compareTo(stepUpThreshold) >= 0;
        return new RiskAssessment(
                large ? 50 : 10,
                large ? Enums.RiskLevel.MEDIUM : Enums.RiskLevel.LOW,
                large ? Decision.STEP_UP : Decision.ALLOW,
                large
                        ? "Risk scoring is temporarily unavailable, so this transfer needs verification."
                        : "Risk scoring is temporarily unavailable; the amount is within your routine range.",
                List.of(new RiskFactor("service_availability", "unavailable", 0,
                        "The fraud detection service did not respond, so a conservative rule was applied.")),
                "fallback_rule_v1",
                true);
    }
}
