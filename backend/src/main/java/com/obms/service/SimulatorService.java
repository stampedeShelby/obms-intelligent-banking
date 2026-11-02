package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.Transaction;
import com.obms.dto.SimulatorDtos;
import com.obms.repository.AccountRepository;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What-if financial projection (innovation module 4).
 *
 * <h2>Read-only by construction</h2>
 * <p>This service takes no repository that can write, calls no ledger method,
 * and every method is {@code readOnly}. A simulation cannot move money or
 * change a balance - that property is structural, not a convention someone
 * has to remember.</p>
 *
 * <h2>Honest arithmetic</h2>
 * <p>Projections are simple, stated, and linear. No investment return is
 * assumed, because assuming one would turn a budgeting tool into an
 * unlicensed financial projection. Every scenario returns its
 * {@code assumptions} list so the customer sees what the number rests on.</p>
 */
@Service
@RequiredArgsConstructor
public class SimulatorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int BASELINE_MONTHS = 3;
    private static final int MAX_HORIZON_MONTHS = 120;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AnalyticsService analyticsService;

    /**
     * Runs a scenario against the customer's actual recent behaviour.
     *
     * @param request the scenario parameters
     * @return current versus projected figures with a month-by-month series
     */
    @Transactional(readOnly = true)
    public SimulatorDtos.SimulationResult simulate(Long customerId,
                                                    SimulatorDtos.SimulationRequest request) {
        int horizon = request.months() == null ? 12 : request.months();
        if (horizon < 1 || horizon > MAX_HORIZON_MONTHS) {
            throw ApiException.badRequest("INVALID_HORIZON",
                    "Choose a horizon between 1 and " + MAX_HORIZON_MONTHS + " months.");
        }

        Baseline baseline = computeBaseline(customerId);
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Baseline income and spending are the averages of your last "
                + BASELINE_MONTHS + " months of activity.");
        assumptions.add("No investment growth or interest is assumed. Figures are nominal.");

        BigDecimal projectedIncome = baseline.monthlyIncome();
        BigDecimal projectedExpense = baseline.monthlyExpense();
        BigDecimal extraMonthlySaving = BigDecimal.ZERO;

        // --- apply the scenario levers -------------------------------------

        if (request.incomeChangePercent() != null
                && request.incomeChangePercent().signum() != 0) {
            projectedIncome = applyPercent(projectedIncome, request.incomeChangePercent());
            assumptions.add("Income changed by " + request.incomeChangePercent()
                    + "%, giving " + projectedIncome + " a month.");
        }

        if (request.categoryReductionPercent() != null && request.category() != null
                && request.categoryReductionPercent().signum() != 0) {
            BigDecimal categorySpend = baseline.spendByCategory()
                    .getOrDefault(request.category(), BigDecimal.ZERO);
            if (categorySpend.signum() == 0) {
                assumptions.add("No spending was found in " + request.category()
                        + " over the baseline period, so this reduction has no effect.");
            } else {
                BigDecimal reduction = categorySpend
                        .multiply(request.categoryReductionPercent())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                projectedExpense = projectedExpense.subtract(reduction).max(BigDecimal.ZERO);
                assumptions.add("Reducing " + request.category() + " spending of "
                        + categorySpend + " by " + request.categoryReductionPercent()
                        + "% frees " + reduction + " a month.");
            }
        }

        if (request.expenseChangePercent() != null
                && request.expenseChangePercent().signum() != 0) {
            projectedExpense = applyPercent(projectedExpense, request.expenseChangePercent());
            assumptions.add("Overall spending changed by " + request.expenseChangePercent()
                    + "%, giving " + projectedExpense + " a month.");
        }

        if (request.monthlySavingAmount() != null
                && request.monthlySavingAmount().signum() > 0) {
            extraMonthlySaving = request.monthlySavingAmount();
            assumptions.add("A deliberate " + extraMonthlySaving
                    + " a month is set aside before discretionary spending.");
        }

        // --- project --------------------------------------------------------

        BigDecimal currentMonthlyNet = baseline.monthlyIncome()
                .subtract(baseline.monthlyExpense());
        BigDecimal projectedMonthlyNet = projectedIncome
                .subtract(projectedExpense)
                .subtract(extraMonthlySaving);

        BigDecimal monthlyAccumulation = projectedMonthlyNet.add(extraMonthlySaving);

        List<SimulatorDtos.ProjectionPoint> series = new ArrayList<>();
        BigDecimal currentRunning = baseline.totalBalance();
        BigDecimal projectedRunning = baseline.totalBalance();
        YearMonth month = YearMonth.now(ZONE);

        for (int i = 1; i <= horizon; i++) {
            month = month.plusMonths(1);
            currentRunning = currentRunning.add(currentMonthlyNet);
            projectedRunning = projectedRunning.add(monthlyAccumulation);
            series.add(new SimulatorDtos.ProjectionPoint(
                    month.toString(),
                    currentRunning.setScale(2, RoundingMode.HALF_UP),
                    projectedRunning.setScale(2, RoundingMode.HALF_UP)));
        }

        BigDecimal currentEnd = baseline.totalBalance()
                .add(currentMonthlyNet.multiply(BigDecimal.valueOf(horizon)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedEnd = baseline.totalBalance()
                .add(monthlyAccumulation.multiply(BigDecimal.valueOf(horizon)))
                .setScale(2, RoundingMode.HALF_UP);

        String verdict = buildVerdict(projectedMonthlyNet, monthlyAccumulation,
                currentEnd, projectedEnd, horizon, baseline);

        return new SimulatorDtos.SimulationResult(
                horizon,
                new SimulatorDtos.ScenarioSide("Current path", baseline.monthlyIncome(),
                        baseline.monthlyExpense(), currentMonthlyNet, currentEnd),
                new SimulatorDtos.ScenarioSide("Proposed path", projectedIncome,
                        projectedExpense, projectedMonthlyNet, projectedEnd),
                projectedEnd.subtract(currentEnd),
                monthlyAccumulation.setScale(2, RoundingMode.HALF_UP),
                series,
                assumptions,
                verdict,
                baseline.monthsOfData() >= 2);
    }

    /**
     * Answers whether a stated monthly saving is affordable.
     *
     * <p>Separate from {@link #simulate} because the question "can I afford
     * this" wants a yes or no with a reason, not a chart.</p>
     */
    @Transactional(readOnly = true)
    public SimulatorDtos.AffordabilityResult canAfford(Long customerId, BigDecimal monthlyAmount) {
        if (monthlyAmount == null || monthlyAmount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Enter an amount greater than zero.");
        }
        Baseline baseline = computeBaseline(customerId);
        BigDecimal surplus = baseline.monthlyIncome().subtract(baseline.monthlyExpense());
        boolean affordable = surplus.compareTo(monthlyAmount) >= 0;

        String reasoning;
        if (baseline.monthsOfData() == 0) {
            reasoning = "There is not enough transaction history yet to judge this reliably.";
        } else if (affordable) {
            reasoning = "Your average monthly surplus is " + surplus
                    + ", which covers " + monthlyAmount + " with "
                    + surplus.subtract(monthlyAmount) + " left over.";
        } else if (surplus.signum() <= 0) {
            reasoning = "Your spending currently matches or exceeds your income, so there is no "
                    + "surplus to commit. Reducing spending comes first.";
        } else {
            reasoning = "Your average monthly surplus is " + surplus + ", which is "
                    + monthlyAmount.subtract(surplus) + " short of " + monthlyAmount
                    + ". A smaller amount would be sustainable.";
        }

        BigDecimal suggested = surplus.signum() <= 0
                ? BigDecimal.ZERO
                : surplus.multiply(BigDecimal.valueOf(0.8)).setScale(2, RoundingMode.HALF_UP);

        return new SimulatorDtos.AffordabilityResult(monthlyAmount, affordable, surplus,
                suggested, reasoning, baseline.monthsOfData() >= 2);
    }

    // ------------------------------------------------------------------

    private Baseline computeBaseline(Long customerId) {
        YearMonth current = YearMonth.now(ZONE);
        Instant from = current.minusMonths(BASELINE_MONTHS - 1L)
                .atDay(1).atStartOfDay(ZONE).toInstant();

        List<Transaction> history = transactionRepository.findCustomerHistory(customerId, from);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<Enums.SpendCategory, BigDecimal> byCategory = new EnumMap<>(Enums.SpendCategory.class);
        List<YearMonth> monthsSeen = new ArrayList<>();

        for (Transaction txn : history) {
            YearMonth month = YearMonth.from(txn.getBookedAt().atZone(ZONE));
            if (!monthsSeen.contains(month)) {
                monthsSeen.add(month);
            }
            if (analyticsService.isIncome(txn)) {
                income = income.add(txn.getAmount());
            } else if (analyticsService.isSpending(txn)) {
                expense = expense.add(txn.getAmount());
                Enums.SpendCategory category = txn.getCategory() == null
                        ? Enums.SpendCategory.OTHER : txn.getCategory();
                byCategory.merge(category, txn.getAmount(), BigDecimal::add);
            }
        }

        int divisor = Math.max(monthsSeen.size(), 1);
        BigDecimal monthlyIncome = income.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
        BigDecimal monthlyExpense = expense.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);

        Map<Enums.SpendCategory, BigDecimal> monthlyByCategory =
                new EnumMap<>(Enums.SpendCategory.class);
        byCategory.forEach((category, total) -> monthlyByCategory.put(category,
                total.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)));

        BigDecimal totalBalance = accountRepository
                .findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Baseline(monthlyIncome, monthlyExpense, monthlyByCategory,
                totalBalance, monthsSeen.size());
    }

    private BigDecimal applyPercent(BigDecimal base, BigDecimal percent) {
        BigDecimal multiplier = BigDecimal.ONE
                .add(percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return base.multiply(multiplier).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildVerdict(BigDecimal projectedNet, BigDecimal accumulation,
                                BigDecimal currentEnd, BigDecimal projectedEnd,
                                int horizon, Baseline baseline) {
        if (baseline.monthsOfData() == 0) {
            return "There is no transaction history to project from yet. "
                    + "Once a month or two of activity exists this will be meaningful.";
        }
        if (projectedNet.signum() < 0) {
            return "This scenario leaves you short by " + projectedNet.abs()
                    + " a month, which would draw down your balance. "
                    + "Reduce the amount set aside, or cut spending further.";
        }
        BigDecimal difference = projectedEnd.subtract(currentEnd);
        if (difference.signum() > 0) {
            return "Over " + horizon + " months this scenario leaves you " + difference
                    + " better off than your current path, accumulating "
                    + accumulation + " a month.";
        }
        if (difference.signum() == 0) {
            return "This scenario makes no material difference to where you end up.";
        }
        return "Over " + horizon + " months this scenario leaves you " + difference.abs()
                + " worse off than your current path.";
    }

    /**
     * Averages derived from recent actual behaviour.
     *
     * @param monthlyIncome   average monthly income
     * @param monthlyExpense  average monthly spending
     * @param spendByCategory average monthly spending per category
     * @param totalBalance    current balance across all accounts
     * @param monthsOfData    how many distinct months had activity
     */
    record Baseline(
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpense,
            Map<Enums.SpendCategory, BigDecimal> spendByCategory,
            BigDecimal totalBalance,
            int monthsOfData
    ) {
    }
}
