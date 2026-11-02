package com.obms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.FinancialScoreSnapshot;
import com.obms.domain.SavingsGoal;
import com.obms.domain.Transaction;
import com.obms.dto.HealthDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.FinancialScoreSnapshotRepository;
import com.obms.repository.SavingsGoalRepository;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes a personal financial wellness score (innovation module 3).
 *
 * <h2>What this is not</h2>
 * <p>This is <strong>not a credit score</strong>. It is computed entirely from
 * the customer's own transaction history inside OBMS, is never shared with any
 * credit bureau, and has no bearing on lending. The UI states this, and the
 * band names avoid credit-score vocabulary deliberately.</p>
 *
 * <h2>The formula</h2>
 * <p>Five weighted indicators sum to 100. Every indicator is a ratio the
 * customer can verify from their own statement, and each one returns the
 * figures it was computed from so the breakdown is auditable rather than
 * asserted:</p>
 * <table>
 *   <caption>Indicator weights</caption>
 *   <tr><td>Savings rate</td><td>30 points</td></tr>
 *   <tr><td>Expense-to-income ratio</td><td>25 points</td></tr>
 *   <tr><td>Spending stability</td><td>15 points</td></tr>
 *   <tr><td>Emergency buffer</td><td>20 points</td></tr>
 *   <tr><td>Savings goal consistency</td><td>10 points</td></tr>
 * </table>
 *
 * <p>Where income cannot be established the score is still produced but
 * {@code dataSufficient} is false and the interpretation says so, rather than
 * quietly scoring a customer against a denominator of zero.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialHealthService {

    static final String FORMULA_VERSION = "v1.0";
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int LOOKBACK_MONTHS = 6;

    private static final int WEIGHT_SAVINGS_RATE = 30;
    private static final int WEIGHT_EXPENSE_RATIO = 25;
    private static final int WEIGHT_STABILITY = 15;
    private static final int WEIGHT_BUFFER = 20;
    private static final int WEIGHT_GOALS = 10;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final FinancialScoreSnapshotRepository snapshotRepository;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    /**
     * Computes the score for a period and stores a snapshot.
     *
     * @param period month to score; defaults to the current month
     */
    @Transactional
    public HealthDtos.FinancialHealth evaluate(Long customerId, YearMonth period) {
        YearMonth target = period == null ? YearMonth.now(ZONE) : period;
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));

        Instant lookbackStart = target.minusMonths(LOOKBACK_MONTHS - 1L)
                .atDay(1).atStartOfDay(ZONE).toInstant();
        List<Transaction> history = transactionRepository.findCustomerHistory(customerId, lookbackStart);

        Inputs inputs = gatherInputs(customer, customerId, history, target);

        List<HealthDtos.IndicatorScore> indicators = new ArrayList<>();
        indicators.add(scoreSavingsRate(inputs));
        indicators.add(scoreExpenseRatio(inputs));
        indicators.add(scoreStability(inputs));
        indicators.add(scoreBuffer(inputs));
        indicators.add(scoreGoalConsistency(inputs));

        int score = indicators.stream().mapToInt(HealthDtos.IndicatorScore::points).sum();
        score = Math.max(0, Math.min(100, score));
        String band = bandFor(score);

        List<HealthDtos.Suggestion> suggestions = buildSuggestions(indicators, inputs);
        persistSnapshot(customer, target, score, band, indicators);

        List<HealthDtos.ScorePoint> trend = snapshotRepository
                .findTop12ByCustomerIdOrderByPeriodDesc(customerId).stream()
                .map(snapshot -> new HealthDtos.ScorePoint(snapshot.getPeriod(), snapshot.getScore()))
                .sorted(Comparator.comparing(HealthDtos.ScorePoint::period))
                .toList();

        return new HealthDtos.FinancialHealth(
                target.toString(), score, band,
                interpret(score, band, inputs), indicators, suggestions, trend,
                FORMULA_VERSION, inputs.dataSufficient());
    }

    @Transactional(readOnly = true)
    public List<HealthDtos.ScorePoint> history(Long customerId) {
        return snapshotRepository.findTop12ByCustomerIdOrderByPeriodDesc(customerId).stream()
                .map(snapshot -> new HealthDtos.ScorePoint(snapshot.getPeriod(), snapshot.getScore()))
                .sorted(Comparator.comparing(HealthDtos.ScorePoint::period))
                .toList();
    }

    // ------------------------------------------------------------------
    // indicator scoring
    // ------------------------------------------------------------------

    /**
     * Savings rate: the share of income not spent.
     *
     * <p>Full marks at 20% or above, which is the commonly cited personal
     * finance benchmark. Scaled linearly below that.</p>
     */
    private HealthDtos.IndicatorScore scoreSavingsRate(Inputs inputs) {
        BigDecimal rate = inputs.income().signum() > 0
                ? inputs.income().subtract(inputs.expense())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(inputs.income(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal capped = rate.max(BigDecimal.ZERO).min(BigDecimal.valueOf(20));
        int points = capped.multiply(BigDecimal.valueOf(WEIGHT_SAVINGS_RATE))
                .divide(BigDecimal.valueOf(20), 0, RoundingMode.HALF_UP)
                .intValue();

        String explanation = inputs.income().signum() == 0
                ? "No income was recorded in this period, so no savings rate could be measured."
                : "You kept " + rate + "% of the " + inputs.income()
                        + " you received. Full marks are awarded at 20% or above.";

        return new HealthDtos.IndicatorScore("savings_rate", "Savings rate", rate, "%",
                WEIGHT_SAVINGS_RATE, points, explanation);
    }

    /**
     * Expense-to-income ratio. Full marks at 50% or below; nothing above 100%.
     */
    private HealthDtos.IndicatorScore scoreExpenseRatio(Inputs inputs) {
        BigDecimal ratio = inputs.income().signum() > 0
                ? inputs.expense().multiply(BigDecimal.valueOf(100))
                        .divide(inputs.income(), 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);

        int points;
        if (ratio.compareTo(BigDecimal.valueOf(50)) <= 0) {
            points = WEIGHT_EXPENSE_RATIO;
        } else if (ratio.compareTo(BigDecimal.valueOf(100)) >= 0) {
            points = 0;
        } else {
            BigDecimal remaining = BigDecimal.valueOf(100).subtract(ratio);
            points = remaining.multiply(BigDecimal.valueOf(WEIGHT_EXPENSE_RATIO))
                    .divide(BigDecimal.valueOf(50), 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return new HealthDtos.IndicatorScore("expense_ratio", "Expense to income", ratio, "%",
                WEIGHT_EXPENSE_RATIO, points,
                "Your spending of " + inputs.expense() + " is " + ratio
                        + "% of income. Full marks at 50% or below.");
    }

    /**
     * Spending stability: how consistent monthly spending is.
     *
     * <p>Measured as the coefficient of variation across the lookback window.
     * Low variation scores well because predictable outgoings are easier to
     * plan around. Needs at least two months of history to mean anything.</p>
     */
    private HealthDtos.IndicatorScore scoreStability(Inputs inputs) {
        List<BigDecimal> monthlySpend = inputs.monthlySpend();
        if (monthlySpend.size() < 2) {
            return new HealthDtos.IndicatorScore("spending_stability", "Spending stability",
                    BigDecimal.ZERO, "%", WEIGHT_STABILITY, WEIGHT_STABILITY / 2,
                    "Not enough history yet to measure stability. Half marks awarded "
                            + "while this builds up.");
        }

        BigDecimal mean = monthlySpend.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(monthlySpend.size()), 2, RoundingMode.HALF_UP);

        if (mean.signum() == 0) {
            return new HealthDtos.IndicatorScore("spending_stability", "Spending stability",
                    BigDecimal.ZERO, "%", WEIGHT_STABILITY, WEIGHT_STABILITY,
                    "No spending recorded, so spending is perfectly stable.");
        }

        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal value : monthlySpend) {
            BigDecimal diff = value.subtract(mean);
            varianceSum = varianceSum.add(diff.multiply(diff));
        }
        BigDecimal variance = varianceSum.divide(
                BigDecimal.valueOf(monthlySpend.size()), 4, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        BigDecimal cv = stdDev.multiply(BigDecimal.valueOf(100))
                .divide(mean, 2, RoundingMode.HALF_UP);

        // Full marks below 20% variation, zero at 80% and above.
        int points;
        if (cv.compareTo(BigDecimal.valueOf(20)) <= 0) {
            points = WEIGHT_STABILITY;
        } else if (cv.compareTo(BigDecimal.valueOf(80)) >= 0) {
            points = 0;
        } else {
            BigDecimal span = BigDecimal.valueOf(80).subtract(cv);
            points = span.multiply(BigDecimal.valueOf(WEIGHT_STABILITY))
                    .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return new HealthDtos.IndicatorScore("spending_stability", "Spending stability", cv, "%",
                WEIGHT_STABILITY, points,
                "Your monthly spending varies by " + cv + "% around an average of "
                        + mean + ". Steadier spending scores higher.");
    }

    /**
     * Emergency buffer: how many months of spending the balance would cover.
     * Full marks at three months.
     */
    private HealthDtos.IndicatorScore scoreBuffer(Inputs inputs) {
        BigDecimal monthlyExpense = inputs.averageMonthlyExpense();
        BigDecimal months = monthlyExpense.signum() > 0
                ? inputs.totalBalance().divide(monthlyExpense, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal capped = months.max(BigDecimal.ZERO).min(BigDecimal.valueOf(3));
        int points = capped.multiply(BigDecimal.valueOf(WEIGHT_BUFFER))
                .divide(BigDecimal.valueOf(3), 0, RoundingMode.HALF_UP)
                .intValue();

        String explanation = monthlyExpense.signum() == 0
                ? "No regular spending recorded, so a buffer cannot be measured. "
                        + "Full marks awarded."
                : "Your balance of " + inputs.totalBalance() + " covers " + months
                        + " months of typical spending. Full marks at 3 months.";
        if (monthlyExpense.signum() == 0) {
            points = WEIGHT_BUFFER;
        }

        return new HealthDtos.IndicatorScore("emergency_buffer", "Emergency buffer", months,
                "months", WEIGHT_BUFFER, points, explanation);
    }

    /**
     * Savings goal consistency: progress across active goals.
     */
    private HealthDtos.IndicatorScore scoreGoalConsistency(Inputs inputs) {
        if (inputs.activeGoals() == 0) {
            return new HealthDtos.IndicatorScore("goal_consistency", "Savings goal progress",
                    BigDecimal.ZERO, "%", WEIGHT_GOALS, 0,
                    "You have no active savings goals. Creating one and contributing to it "
                            + "earns up to " + WEIGHT_GOALS + " points.");
        }

        BigDecimal averageProgress = inputs.averageGoalProgress();
        int points = averageProgress.min(BigDecimal.valueOf(100))
                .multiply(BigDecimal.valueOf(WEIGHT_GOALS))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .intValue();

        return new HealthDtos.IndicatorScore("goal_consistency", "Savings goal progress",
                averageProgress, "%", WEIGHT_GOALS, points,
                "Across " + inputs.activeGoals() + " active goals you are "
                        + averageProgress + "% of the way to target on average.");
    }

    // ------------------------------------------------------------------
    // supporting computation
    // ------------------------------------------------------------------

    private Inputs gatherInputs(Customer customer, Long customerId,
                                List<Transaction> history, YearMonth target) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        List<BigDecimal> monthlySpend = new ArrayList<>();
        for (int offset = LOOKBACK_MONTHS - 1; offset >= 0; offset--) {
            YearMonth month = target.minusMonths(offset);
            BigDecimal monthSpend = BigDecimal.ZERO;
            for (Transaction txn : history) {
                if (!YearMonth.from(txn.getBookedAt().atZone(ZONE)).equals(month)) {
                    continue;
                }
                if (analyticsService.isSpending(txn)) {
                    monthSpend = monthSpend.add(txn.getAmount());
                }
            }
            monthlySpend.add(monthSpend);
        }

        for (Transaction txn : history) {
            if (!YearMonth.from(txn.getBookedAt().atZone(ZONE)).equals(target)) {
                continue;
            }
            if (analyticsService.isIncome(txn)) {
                income = income.add(txn.getAmount());
            } else if (analyticsService.isSpending(txn)) {
                expense = expense.add(txn.getAmount());
            }
        }

        // Fall back to the declared income when no salary credit was observed,
        // so a customer whose salary is paid elsewhere is not scored against
        // zero income. Which source was used is surfaced in dataSufficient.
        boolean observedIncome = income.signum() > 0;
        if (!observedIncome && customer.getDeclaredMonthlyIncome() != null
                && customer.getDeclaredMonthlyIncome().signum() > 0) {
            income = customer.getDeclaredMonthlyIncome();
        }

        BigDecimal totalBalance = accountRepository
                .findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SavingsGoal> goals = savingsGoalRepository
                .findByCustomerIdAndStatus(customerId, Enums.GoalStatus.ACTIVE);
        BigDecimal averageGoalProgress = goals.isEmpty()
                ? BigDecimal.ZERO
                : goals.stream().map(SavingsGoal::progressPercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(goals.size()), 2, RoundingMode.HALF_UP);

        long monthsWithData = monthlySpend.stream().filter(value -> value.signum() > 0).count();
        BigDecimal averageMonthlyExpense = monthsWithData == 0
                ? BigDecimal.ZERO
                : monthlySpend.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(monthsWithData), 2, RoundingMode.HALF_UP);

        boolean sufficient = income.signum() > 0 && monthsWithData >= 2;

        return new Inputs(income, expense, totalBalance, monthlySpend, averageMonthlyExpense,
                goals.size(), averageGoalProgress, sufficient);
    }

    private List<HealthDtos.Suggestion> buildSuggestions(List<HealthDtos.IndicatorScore> indicators,
                                                          Inputs inputs) {
        List<HealthDtos.Suggestion> suggestions = new ArrayList<>();

        for (HealthDtos.IndicatorScore indicator : indicators) {
            int shortfall = indicator.weight() - indicator.points();
            if (shortfall <= 2) {
                continue;
            }
            switch (indicator.indicator()) {
                case "savings_rate" -> suggestions.add(new HealthDtos.Suggestion(
                        "Raise your savings rate toward 20%",
                        "You are saving " + indicator.observed() + "% of income. Setting aside "
                                + inputs.income().multiply(BigDecimal.valueOf(0.20))
                                        .setScale(2, RoundingMode.HALF_UP)
                                + " a month would reach the benchmark.",
                        shortfall));
                case "expense_ratio" -> suggestions.add(new HealthDtos.Suggestion(
                        "Bring spending below half your income",
                        "Spending is " + indicator.observed()
                                + "% of income. Use the What-If simulator to test a cut "
                                + "in your largest category.",
                        shortfall));
                case "spending_stability" -> suggestions.add(new HealthDtos.Suggestion(
                        "Even out your monthly spending",
                        "Your spending swings by " + indicator.observed()
                                + "% month to month, which makes budgeting harder.",
                        shortfall));
                case "emergency_buffer" -> suggestions.add(new HealthDtos.Suggestion(
                        "Build toward three months of expenses",
                        "Your balance currently covers " + indicator.observed()
                                + " months. Growing this is the single largest "
                                + "protection against a shock.",
                        shortfall));
                case "goal_consistency" -> suggestions.add(new HealthDtos.Suggestion(
                        inputs.activeGoals() == 0
                                ? "Create a savings goal"
                                : "Keep contributing to your goals",
                        inputs.activeGoals() == 0
                                ? "Customers with a named goal save more consistently. "
                                        + "Even a small monthly amount counts."
                                : "You are " + indicator.observed()
                                        + "% of the way there on average.",
                        shortfall));
                default -> log.debug("No suggestion template for {}", indicator.indicator());
            }
        }

        suggestions.sort(Comparator.comparingInt(HealthDtos.Suggestion::pointsAtStake).reversed());
        return suggestions;
    }

    private void persistSnapshot(Customer customer, YearMonth period, int score, String band,
                                 List<HealthDtos.IndicatorScore> indicators) {
        FinancialScoreSnapshot snapshot = snapshotRepository
                .findByCustomerIdAndPeriod(customer.getId(), period.toString())
                .orElseGet(() -> {
                    FinancialScoreSnapshot fresh = new FinancialScoreSnapshot();
                    fresh.setCustomer(customer);
                    fresh.setPeriod(period.toString());
                    return fresh;
                });

        snapshot.setScore(score);
        snapshot.setBand(band);
        snapshot.setFormulaVersion(FORMULA_VERSION);
        try {
            snapshot.setBreakdownJson(objectMapper.writeValueAsString(indicators));
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise score breakdown", ex);
            snapshot.setBreakdownJson("[]");
        }
        snapshotRepository.save(snapshot);
    }

    static String bandFor(int score) {
        if (score >= 80) {
            return "EXCELLENT";
        }
        if (score >= 60) {
            return "GOOD";
        }
        if (score >= 40) {
            return "FAIR";
        }
        return "NEEDS_ATTENTION";
    }

    private String interpret(int score, String band, Inputs inputs) {
        String base = switch (band) {
            case "EXCELLENT" -> "Your finances look well managed. You are saving a healthy share "
                    + "of what you earn and hold a meaningful buffer.";
            case "GOOD" -> "You are in reasonable shape, with room to strengthen one or two areas.";
            case "FAIR" -> "There are clear areas to work on. The suggestions below are ordered "
                    + "by how many points each would recover.";
            default -> "Several indicators need attention. Start with the first suggestion below, "
                    + "which carries the most weight.";
        };
        if (!inputs.dataSufficient()) {
            base += " Note that this score is based on limited history, so treat it as "
                    + "indicative rather than settled.";
        }
        return base + " This is a wellness score computed from your own OBMS activity. "
                + "It is not a credit score and is not shared with anyone.";
    }

    /**
     * The measured inputs to the formula.
     *
     * @param income                income in the scored month
     * @param expense               spending in the scored month
     * @param totalBalance          balance across all the customer's accounts
     * @param monthlySpend          spend per month across the lookback window
     * @param averageMonthlyExpense average across months that had activity
     * @param activeGoals           number of active savings goals
     * @param averageGoalProgress   mean progress across those goals
     * @param dataSufficient        whether there is enough history to score confidently
     */
    record Inputs(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal totalBalance,
            List<BigDecimal> monthlySpend,
            BigDecimal averageMonthlyExpense,
            int activeGoals,
            BigDecimal averageGoalProgress,
            boolean dataSufficient
    ) {
    }
}
