package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.SavingsGoal;
import com.obms.dto.SavingsGoalDtos;
import com.obms.repository.CustomerRepository;
import com.obms.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Savings goals with behaviour-aware recommendations (innovation module 6).
 *
 * <p>The recommendation is the part that makes this more than a progress bar:
 * rather than telling the customer what they must save, it compares that
 * against what their actual surplus supports and says plainly when a goal is
 * not achievable on the current trajectory. A goal tracker that cheerfully
 * reports "save 40,000 a month" to someone with a 6,000 surplus is not
 * helping anybody.</p>
 *
 * <p>Contributions are real money movements: funding a goal debits the chosen
 * account through {@link LedgerService}, so the balance and the goal stay
 * consistent and the contribution appears on the statement.</p>
 */
@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final SavingsGoalRepository savingsGoalRepository;
    private final CustomerRepository customerRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final SimulatorService simulatorService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SavingsGoalDtos.GoalView> list(Long customerId) {
        BigDecimal surplus = surplusFor(customerId);
        return savingsGoalRepository.findByCustomerIdOrderByTargetDateAsc(customerId).stream()
                .map(goal -> enrich(goal, surplus))
                .toList();
    }

    @Transactional
    public SavingsGoalDtos.GoalView create(Long customerId,
                                            SavingsGoalDtos.CreateGoalRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));

        SavingsGoal goal = new SavingsGoal();
        goal.setCustomer(customer);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        goal.setIcon(request.icon());
        SavingsGoal saved = savingsGoalRepository.save(goal);

        auditService.record("goal.created", "SavingsGoal", String.valueOf(saved.getId()),
                "Goal " + saved.getName() + " for " + saved.getTargetAmount());

        return enrich(saved, surplusFor(customerId));
    }

    /**
     * Moves money into a goal, debiting the funding account.
     */
    @Transactional
    public SavingsGoalDtos.GoalView contribute(Long customerId, Long goalId,
                                                SavingsGoalDtos.ContributeRequest request) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndCustomerId(goalId, customerId)
                .orElseThrow(() -> ApiException.notFound("Savings goal"));

        if (goal.getStatus() != Enums.GoalStatus.ACTIVE) {
            throw ApiException.invalidState("This goal is " + goal.getStatus() + ".");
        }

        Account account = accountService.requireOwned(request.accountId(), customerId);
        if (request.amount().compareTo(account.availableBalance()) > 0) {
            throw ApiException.insufficientFunds();
        }

        ledgerService.postDebit(account.getId(), request.amount(),
                Enums.TransactionType.INTERNAL_TRANSFER,
                "Savings goal - " + goal.getName(),
                "Savings goal: " + goal.getName(),
                Enums.SpendCategory.SAVINGS);

        goal.setSavedAmount(goal.getSavedAmount().add(request.amount()));

        boolean achieved = goal.getSavedAmount().compareTo(goal.getTargetAmount()) >= 0;
        if (achieved) {
            goal.setStatus(Enums.GoalStatus.ACHIEVED);
            notificationService.notify(goal.getCustomer(), Enums.NotificationType.SAVINGS_GOAL,
                    "Goal reached: " + goal.getName(),
                    "You have reached your target of " + goal.getTargetAmount() + ". Well done.",
                    "/savings-goals");
        } else {
            notificationService.notify(goal.getCustomer(), Enums.NotificationType.SAVINGS_GOAL,
                    "Progress on " + goal.getName(),
                    request.amount() + " added. You are now at " + goal.progressPercent()
                            + "% of " + goal.getTargetAmount() + ".",
                    "/savings-goals");
        }

        auditService.record("goal.contribution", "SavingsGoal", String.valueOf(goalId),
                "Contributed " + request.amount());

        return enrich(goal, surplusFor(customerId));
    }

    @Transactional
    public void abandon(Long customerId, Long goalId) {
        SavingsGoal goal = savingsGoalRepository.findByIdAndCustomerId(goalId, customerId)
                .orElseThrow(() -> ApiException.notFound("Savings goal"));
        goal.setStatus(Enums.GoalStatus.ABANDONED);
        auditService.record("goal.abandoned", "SavingsGoal", String.valueOf(goalId),
                "Goal " + goal.getName() + " abandoned");
    }

    // ------------------------------------------------------------------

    /**
     * Derives the monthly requirement and compares it against capacity.
     */
    private SavingsGoalDtos.GoalView enrich(SavingsGoal goal, BigDecimal monthlySurplus) {
        long monthsRemaining = Math.max(0,
                ChronoUnit.MONTHS.between(LocalDate.now(ZONE), goal.getTargetDate()));

        BigDecimal remaining = goal.remainingAmount();
        BigDecimal requiredMonthly = monthsRemaining == 0
                ? remaining
                : remaining.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.CEILING);

        // Recommend at most 80% of surplus, leaving headroom for the unexpected.
        BigDecimal recommendedMonthly = monthlySurplus.signum() <= 0
                ? BigDecimal.ZERO
                : monthlySurplus.multiply(BigDecimal.valueOf(0.8))
                        .setScale(2, RoundingMode.HALF_UP);

        boolean onTrack = goal.getStatus() == Enums.GoalStatus.ACHIEVED
                || recommendedMonthly.compareTo(requiredMonthly) >= 0;

        String advice = buildAdvice(goal, monthsRemaining, requiredMonthly,
                recommendedMonthly, monthlySurplus, onTrack);

        return SavingsGoalDtos.GoalView.of(goal, requiredMonthly, recommendedMonthly,
                onTrack, monthsRemaining, advice);
    }

    private String buildAdvice(SavingsGoal goal, long monthsRemaining, BigDecimal requiredMonthly,
                               BigDecimal recommendedMonthly, BigDecimal surplus, boolean onTrack) {
        if (goal.getStatus() == Enums.GoalStatus.ACHIEVED) {
            return "Target reached.";
        }
        if (goal.getStatus() == Enums.GoalStatus.ABANDONED) {
            return "This goal is no longer active.";
        }
        if (monthsRemaining == 0) {
            return "The target date has arrived. " + goal.remainingAmount()
                    + " is still outstanding, so either top it up now or move the date.";
        }
        if (surplus.signum() <= 0) {
            return "Your spending currently matches or exceeds your income, so there is no "
                    + "surplus to commit. Reducing spending comes before funding this goal.";
        }
        if (onTrack) {
            return "Set aside " + requiredMonthly + " a month for " + monthsRemaining
                    + " months to reach " + goal.getTargetAmount()
                    + ". Your surplus comfortably supports this.";
        }

        long achievableMonths = recommendedMonthly.signum() <= 0
                ? 0
                : goal.remainingAmount()
                        .divide(recommendedMonthly, 0, RoundingMode.CEILING)
                        .longValue();

        return "Reaching this by " + goal.getTargetDate() + " needs " + requiredMonthly
                + " a month, but your surplus realistically supports about "
                + recommendedMonthly + ". At that rate it would take roughly "
                + achievableMonths + " months. Either extend the date or lower the target.";
    }

    /**
     * The customer's measured monthly surplus, reused from the simulator so
     * both features answer with the same number.
     */
    private BigDecimal surplusFor(Long customerId) {
        try {
            return simulatorService.canAfford(customerId, BigDecimal.ONE).monthlySurplus();
        } catch (ApiException ex) {
            return BigDecimal.ZERO;
        }
    }
}
