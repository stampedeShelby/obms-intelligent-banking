package com.obms.service;

import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.dto.AnalyticsDtos;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spending analysis over a customer's own ledger (innovation module 5).
 *
 * <p>Two definitions used consistently throughout, because getting them wrong
 * is how spending dashboards end up lying to people:</p>
 * <ul>
 *   <li><b>Spending</b> excludes transfers between the customer's own accounts.
 *       Moving money from savings to current is not an expense, and counting it
 *       would inflate every total.</li>
 *   <li><b>Income</b> is credits received. A credit that is the other leg of an
 *       own-account transfer is likewise excluded.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int TREND_MONTHS = 12;
    private static final int RECURRING_MIN_OCCURRENCES = 3;

    private final TransactionRepository transactionRepository;

    /**
     * Builds the spending analytics payload.
     *
     * @param period the month to analyse; defaults to the current month
     */
    @Transactional(readOnly = true)
    public AnalyticsDtos.SpendingAnalytics analyse(Long customerId, YearMonth period) {
        YearMonth target = period == null ? YearMonth.now(ZONE) : period;
        Instant windowStart = target.minusMonths(TREND_MONTHS - 1L)
                .atDay(1).atStartOfDay(ZONE).toInstant();

        List<Transaction> history = transactionRepository.findCustomerHistory(customerId, windowStart);

        List<Transaction> monthRows = history.stream()
                .filter(txn -> YearMonth.from(txn.getBookedAt().atZone(ZONE)).equals(target))
                .toList();

        List<Transaction> spendRows = monthRows.stream().filter(this::isSpending).toList();
        List<Transaction> incomeRows = monthRows.stream().filter(this::isIncome).toList();

        BigDecimal totalSpend = sum(spendRows);
        BigDecimal totalIncome = sum(incomeRows);

        List<AnalyticsDtos.CategorySlice> categories = buildCategories(spendRows, totalSpend);
        List<AnalyticsDtos.MonthlyTotals> trend = buildTrend(history, target);
        BigDecimal monthOverMonth = computeMonthOverMonth(trend, target);
        List<AnalyticsDtos.RecurringExpense> recurring = detectRecurring(history);
        long uncategorised = spendRows.stream()
                .filter(txn -> txn.getCategory() == null
                        || txn.getCategory() == Enums.SpendCategory.OTHER)
                .count();

        List<AnalyticsDtos.Insight> insights = buildInsights(
                totalSpend, totalIncome, categories, monthOverMonth, recurring, uncategorised);

        return new AnalyticsDtos.SpendingAnalytics(
                target.toString(), totalSpend, totalIncome, categories, trend,
                monthOverMonth, recurring, insights, uncategorised);
    }

    // ------------------------------------------------------------------
    // building blocks
    // ------------------------------------------------------------------

    private List<AnalyticsDtos.CategorySlice> buildCategories(List<Transaction> spendRows,
                                                               BigDecimal totalSpend) {
        Map<Enums.SpendCategory, BigDecimal> totals = new EnumMap<>(Enums.SpendCategory.class);
        Map<Enums.SpendCategory, Long> counts = new EnumMap<>(Enums.SpendCategory.class);

        for (Transaction txn : spendRows) {
            Enums.SpendCategory category = txn.getCategory() == null
                    ? Enums.SpendCategory.OTHER : txn.getCategory();
            totals.merge(category, txn.getAmount(), BigDecimal::add);
            counts.merge(category, 1L, Long::sum);
        }

        List<AnalyticsDtos.CategorySlice> slices = new ArrayList<>();
        totals.forEach((category, total) -> slices.add(new AnalyticsDtos.CategorySlice(
                category, total, percentage(total, totalSpend), counts.getOrDefault(category, 0L))));

        slices.sort(Comparator.comparing(AnalyticsDtos.CategorySlice::total).reversed());
        return slices;
    }

    private List<AnalyticsDtos.MonthlyTotals> buildTrend(List<Transaction> history, YearMonth target) {
        Map<YearMonth, BigDecimal> spendByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> incomeByMonth = new HashMap<>();

        for (Transaction txn : history) {
            YearMonth month = YearMonth.from(txn.getBookedAt().atZone(ZONE));
            if (isSpending(txn)) {
                spendByMonth.merge(month, txn.getAmount(), BigDecimal::add);
            } else if (isIncome(txn)) {
                incomeByMonth.merge(month, txn.getAmount(), BigDecimal::add);
            }
        }

        List<AnalyticsDtos.MonthlyTotals> trend = new ArrayList<>();
        for (int offset = TREND_MONTHS - 1; offset >= 0; offset--) {
            YearMonth month = target.minusMonths(offset);
            BigDecimal income = incomeByMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal expense = spendByMonth.getOrDefault(month, BigDecimal.ZERO);
            trend.add(new AnalyticsDtos.MonthlyTotals(
                    month.toString(), income, expense, income.subtract(expense)));
        }
        return trend;
    }

    /**
     * Change in spending against the previous month, as a percentage.
     *
     * @return the percentage change, or zero when the previous month had no
     *         spending (a jump from nothing is not a meaningful percentage)
     */
    private BigDecimal computeMonthOverMonth(List<AnalyticsDtos.MonthlyTotals> trend,
                                             YearMonth target) {
        String current = target.toString();
        String previous = target.minusMonths(1).toString();

        BigDecimal currentSpend = BigDecimal.ZERO;
        BigDecimal previousSpend = BigDecimal.ZERO;
        for (AnalyticsDtos.MonthlyTotals month : trend) {
            if (month.period().equals(current)) {
                currentSpend = month.expense();
            } else if (month.period().equals(previous)) {
                previousSpend = month.expense();
            }
        }
        if (previousSpend.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return currentSpend.subtract(previousSpend)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousSpend, 2, RoundingMode.HALF_UP);
    }

    /**
     * Finds charges that repeat under the same label.
     *
     * <p>A label counts as recurring when it appears in at least three distinct
     * months. Requiring distinct months rather than three occurrences avoids
     * labelling three coffees in one week as a subscription.</p>
     */
    private List<AnalyticsDtos.RecurringExpense> detectRecurring(List<Transaction> history) {
        Map<String, List<Transaction>> byLabel = new HashMap<>();
        for (Transaction txn : history) {
            if (!isSpending(txn)) {
                continue;
            }
            String label = label(txn);
            if (label != null) {
                byLabel.computeIfAbsent(label, key -> new ArrayList<>()).add(txn);
            }
        }

        List<AnalyticsDtos.RecurringExpense> recurring = new ArrayList<>();
        byLabel.forEach((label, rows) -> {
            long distinctMonths = rows.stream()
                    .map(txn -> YearMonth.from(txn.getBookedAt().atZone(ZONE)))
                    .distinct()
                    .count();
            if (distinctMonths >= RECURRING_MIN_OCCURRENCES) {
                recurring.add(new AnalyticsDtos.RecurringExpense(
                        label, distinctMonths, typicalAmount(rows),
                        rows.get(0).getCategory() == null
                                ? Enums.SpendCategory.OTHER : rows.get(0).getCategory()));
            }
        });

        recurring.sort(Comparator.comparing(AnalyticsDtos.RecurringExpense::typicalAmount).reversed());
        return recurring;
    }

    private List<AnalyticsDtos.Insight> buildInsights(BigDecimal totalSpend, BigDecimal totalIncome,
                                                       List<AnalyticsDtos.CategorySlice> categories,
                                                       BigDecimal monthOverMonth,
                                                       List<AnalyticsDtos.RecurringExpense> recurring,
                                                       long uncategorised) {
        List<AnalyticsDtos.Insight> insights = new ArrayList<>();

        if (totalIncome.signum() > 0 && totalSpend.compareTo(totalIncome) > 0) {
            insights.add(new AnalyticsDtos.Insight(
                    "You spent more than you received this month",
                    "Spending of " + totalSpend + " exceeded income of " + totalIncome
                            + ", a shortfall of " + totalSpend.subtract(totalIncome)
                            + " drawn from your balance.",
                    "WARNING"));
        }

        if (monthOverMonth.compareTo(BigDecimal.valueOf(20)) > 0) {
            insights.add(new AnalyticsDtos.Insight(
                    "Spending is up sharply",
                    "You spent " + monthOverMonth + "% more than last month.",
                    "WARNING"));
        } else if (monthOverMonth.compareTo(BigDecimal.valueOf(-10)) < 0) {
            insights.add(new AnalyticsDtos.Insight(
                    "Spending is down",
                    "You spent " + monthOverMonth.abs() + "% less than last month. "
                            + "Consider moving the difference into a savings goal.",
                    "INFO"));
        }

        if (!categories.isEmpty()) {
            AnalyticsDtos.CategorySlice top = categories.get(0);
            if (top.share().compareTo(BigDecimal.valueOf(40)) > 0) {
                insights.add(new AnalyticsDtos.Insight(
                        top.category() + " dominates your spending",
                        top.share() + "% of this month's spending went to "
                                + top.category() + " (" + top.total() + "). "
                                + "Trimming it by 10% would free up "
                                + top.total().multiply(BigDecimal.valueOf(0.10))
                                        .setScale(2, RoundingMode.HALF_UP) + ".",
                        "SUGGESTION"));
            }
        }

        if (!recurring.isEmpty()) {
            BigDecimal recurringTotal = recurring.stream()
                    .map(AnalyticsDtos.RecurringExpense::typicalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            insights.add(new AnalyticsDtos.Insight(
                    recurring.size() + " recurring charges detected",
                    "These repeat most months and total about " + recurringTotal
                            + " each month. Review any you no longer use.",
                    "SUGGESTION"));
        }

        if (uncategorised > 0) {
            insights.add(new AnalyticsDtos.Insight(
                    uncategorised + " transactions are uncategorised",
                    "Assign categories to these so your breakdown is complete. "
                            + "Your corrections are kept and not overwritten.",
                    "INFO"));
        }

        if (insights.isEmpty()) {
            insights.add(new AnalyticsDtos.Insight(
                    "Nothing unusual this month",
                    "Your spending is in line with your recent pattern.",
                    "INFO"));
        }
        return insights;
    }

    // ------------------------------------------------------------------
    // classification helpers, shared with the health and copilot modules
    // ------------------------------------------------------------------

    /**
     * @return true when the row represents real outgoing spending
     */
    public boolean isSpending(Transaction txn) {
        return txn.getDirection() == Enums.TransactionDirection.DEBIT
                && txn.getStatus() == Enums.TransactionStatus.COMPLETED
                && txn.getCategory() != Enums.SpendCategory.TRANSFER
                && txn.getTransactionType() != Enums.TransactionType.INTERNAL_TRANSFER;
    }

    /**
     * @return true when the row represents money genuinely received
     */
    public boolean isIncome(Transaction txn) {
        return txn.getDirection() == Enums.TransactionDirection.CREDIT
                && txn.getStatus() == Enums.TransactionStatus.COMPLETED
                && txn.getTransactionType() != Enums.TransactionType.INTERNAL_TRANSFER;
    }

    private String label(Transaction txn) {
        if (txn.getCounterpartyName() != null && !txn.getCounterpartyName().isBlank()) {
            return txn.getCounterpartyName();
        }
        if (txn.getDescription() != null && !txn.getDescription().isBlank()) {
            String description = txn.getDescription();
            int separator = description.indexOf(" - ");
            return separator > 0 ? description.substring(0, separator) : description;
        }
        return null;
    }

    private BigDecimal typicalAmount(List<Transaction> rows) {
        List<BigDecimal> amounts = rows.stream()
                .map(Transaction::getAmount)
                .sorted()
                .toList();
        return amounts.get(amounts.size() / 2);
    }

    private BigDecimal sum(List<Transaction> rows) {
        return rows.stream().map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
