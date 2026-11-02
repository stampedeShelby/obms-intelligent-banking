package com.obms.dto;

import com.obms.domain.Enums;

import java.math.BigDecimal;
import java.util.List;

/** Spending analytics projections (innovation module 5). */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
        // DTO holder
    }

    /**
     * One category's share of a period's spending.
     *
     * @param category     the spend category
     * @param total        amount spent in it
     * @param share        percentage of total spending, two decimals
     * @param transactions how many transactions contributed
     */
    public record CategorySlice(
            Enums.SpendCategory category,
            BigDecimal total,
            BigDecimal share,
            long transactions
    ) {
    }

    /**
     * Totals for one calendar month.
     *
     * @param period  month in {@code yyyy-MM} form
     * @param income  total credits
     * @param expense total debits excluding transfers between own accounts
     * @param net     income less expense
     */
    public record MonthlyTotals(
            String period,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net
    ) {
    }

    /**
     * A recurring expense the module detected.
     *
     * @param label       counterparty or description the charge repeats under
     * @param occurrences how many times it appeared
     * @param typicalAmount median-ish typical amount
     * @param category    its spend category
     */
    public record RecurringExpense(
            String label,
            long occurrences,
            BigDecimal typicalAmount,
            Enums.SpendCategory category
    ) {
    }

    /**
     * A suggestion the module derived from the customer's own numbers.
     *
     * @param title    short headline
     * @param detail   the reasoning, including the figures it rests on
     * @param severity INFO, SUGGESTION or WARNING
     */
    public record Insight(String title, String detail, String severity) {
    }

    /**
     * Full analytics payload for the spending screen.
     *
     * @param period            the month analysed
     * @param totalSpend        total debits in the period, transfers excluded
     * @param totalIncome       total credits in the period
     * @param categories        breakdown by category, largest first
     * @param trend             up to twelve months of totals, oldest first
     * @param monthOverMonth    change in spend against the previous month, percent
     * @param recurring         detected recurring expenses
     * @param insights          generated suggestions
     * @param uncategorisedCount transactions that matched no rule
     */
    public record SpendingAnalytics(
            String period,
            BigDecimal totalSpend,
            BigDecimal totalIncome,
            List<CategorySlice> categories,
            List<MonthlyTotals> trend,
            BigDecimal monthOverMonth,
            List<RecurringExpense> recurring,
            List<Insight> insights,
            long uncategorisedCount
    ) {
    }
}
