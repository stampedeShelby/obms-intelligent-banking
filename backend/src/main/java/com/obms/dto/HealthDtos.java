package com.obms.dto;

import java.math.BigDecimal;
import java.util.List;

/** Financial health score projections (innovation module 3). */
public final class HealthDtos {

    private HealthDtos() {
        // DTO holder
    }

    /**
     * One scored indicator.
     *
     * @param indicator   machine name, e.g. {@code savings_rate}
     * @param label       human label for the UI
     * @param observed    the measured value
     * @param unit        unit of the observed value, e.g. {@code %}
     * @param weight      maximum points this indicator can contribute
     * @param points      points actually awarded
     * @param explanation how the points were arrived at, in plain language
     */
    public record IndicatorScore(
            String indicator,
            String label,
            BigDecimal observed,
            String unit,
            int weight,
            int points,
            String explanation
    ) {
    }

    /**
     * A suggestion for improving the score.
     *
     * @param action        what to do
     * @param rationale     why, with the customer's own figures
     * @param pointsAtStake how many score points this could recover
     */
    public record Suggestion(String action, String rationale, int pointsAtStake) {
    }

    /**
     * Full financial health payload.
     *
     * @param period          month scored, {@code yyyy-MM}
     * @param score           0-100 wellness score
     * @param band            EXCELLENT, GOOD, FAIR or NEEDS_ATTENTION
     * @param interpretation  one-paragraph reading of the score
     * @param indicators      per-indicator breakdown summing to the score
     * @param suggestions     ranked improvement actions
     * @param history         previous months' scores, oldest first
     * @param formulaVersion  which version of the formula produced this
     * @param dataSufficient  false when there is too little history to score well
     */
    public record FinancialHealth(
            String period,
            int score,
            String band,
            String interpretation,
            List<IndicatorScore> indicators,
            List<Suggestion> suggestions,
            List<ScorePoint> history,
            String formulaVersion,
            boolean dataSufficient
    ) {
    }

    /**
     * A historical score for the trend chart.
     *
     * @param period month in {@code yyyy-MM} form
     * @param score  the score recorded for that month
     */
    public record ScorePoint(String period, int score) {
    }
}
