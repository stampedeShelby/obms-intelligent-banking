package com.obms.dto;

import com.obms.domain.Enums;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.util.List;

/** What-if simulator request and response shapes (innovation module 4). */
public final class SimulatorDtos {

    private SimulatorDtos() {
        // DTO holder
    }

    /**
     * Scenario parameters. Every lever is optional; supplying none projects the
     * current path forward unchanged, which is a useful baseline in itself.
     *
     * @param months                   horizon, 1 to 120, defaults to 12
     * @param monthlySavingAmount      a fixed amount to set aside each month
     * @param expenseChangePercent     change to overall spending, e.g. -10
     * @param incomeChangePercent      change to income, e.g. -10 for a pay cut
     * @param category                 category to target with a reduction
     * @param categoryReductionPercent how much to cut that category by
     */
    public record SimulationRequest(
            @Min(value = 1, message = "Horizon must be at least 1 month")
            @Max(value = 120, message = "Horizon may be at most 120 months")
            Integer months,

            @DecimalMin(value = "0.00", message = "Saving amount cannot be negative")
            BigDecimal monthlySavingAmount,

            @DecimalMin(value = "-100.00", message = "Spending cannot fall by more than 100%")
            @DecimalMax(value = "500.00", message = "That spending increase is out of range")
            BigDecimal expenseChangePercent,

            @DecimalMin(value = "-100.00", message = "Income cannot fall by more than 100%")
            @DecimalMax(value = "500.00", message = "That income increase is out of range")
            BigDecimal incomeChangePercent,

            Enums.SpendCategory category,

            @DecimalMin(value = "0.00", message = "Reduction cannot be negative")
            @DecimalMax(value = "100.00", message = "Reduction cannot exceed 100%")
            BigDecimal categoryReductionPercent
    ) {
    }

    /**
     * One side of the comparison.
     *
     * @param label            "Current path" or "Proposed path"
     * @param monthlyIncome    income per month on this path
     * @param monthlyExpense   spending per month on this path
     * @param monthlyNet       income less spending and any deliberate saving
     * @param endBalance       projected balance at the end of the horizon
     */
    public record ScenarioSide(
            String label,
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpense,
            BigDecimal monthlyNet,
            BigDecimal endBalance
    ) {
    }

    /**
     * A point on the projection chart.
     *
     * @param period    month in {@code yyyy-MM} form
     * @param current   balance on the current path
     * @param projected balance on the proposed path
     */
    public record ProjectionPoint(String period, BigDecimal current, BigDecimal projected) {
    }

    /**
     * Simulation output.
     *
     * @param months            horizon simulated
     * @param current           the current path
     * @param proposed          the proposed path
     * @param difference        proposed end balance less current end balance
     * @param monthlyAccumulation how much the proposed path adds per month
     * @param series            month-by-month projection for charting
     * @param assumptions       every assumption the numbers rest on
     * @param verdict           plain-language conclusion
     * @param dataSufficient    false when there is too little history to rely on
     */
    public record SimulationResult(
            int months,
            ScenarioSide current,
            ScenarioSide proposed,
            BigDecimal difference,
            BigDecimal monthlyAccumulation,
            List<ProjectionPoint> series,
            List<String> assumptions,
            String verdict,
            boolean dataSufficient
    ) {
    }

    /**
     * Answer to "can I afford to save X every month".
     *
     * @param requestedAmount  the amount asked about
     * @param affordable       whether the surplus covers it
     * @param monthlySurplus   measured average surplus
     * @param suggestedAmount  a sustainable alternative, 80% of surplus
     * @param reasoning        the explanation, with figures
     * @param dataSufficient   false when history is too thin
     */
    public record AffordabilityResult(
            BigDecimal requestedAmount,
            boolean affordable,
            BigDecimal monthlySurplus,
            BigDecimal suggestedAmount,
            String reasoning,
            boolean dataSufficient
    ) {
    }
}
