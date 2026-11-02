package com.obms.dto;

import com.obms.domain.Enums;
import com.obms.domain.SavingsGoal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Savings goal request and response shapes (innovation module 6). */
public final class SavingsGoalDtos {

    private SavingsGoalDtos() {
        // DTO holder
    }

    public record CreateGoalRequest(
            @NotBlank(message = "Give the goal a name")
            @Size(max = 120)
            String name,

            @NotNull(message = "Enter a target amount")
            @DecimalMin(value = "1.00", message = "Target must be at least 1.00")
            @Digits(integer = 15, fraction = 2)
            BigDecimal targetAmount,

            @NotNull(message = "Choose a target date")
            @Future(message = "The target date must be in the future")
            LocalDate targetDate,

            @Size(max = 40)
            String icon
    ) {
    }

    public record ContributeRequest(
            @NotNull(message = "Enter an amount")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
            @Digits(integer = 15, fraction = 2)
            BigDecimal amount,

            @NotNull(message = "Select the account to fund this from")
            Long accountId
    ) {
    }

    /**
     * A goal with its derived figures.
     *
     * @param requiredMonthly  what must be set aside monthly to hit the target
     * @param recommendedMonthly what the customer's surplus actually supports
     * @param onTrack          whether the recommendation covers the requirement
     * @param monthsRemaining  whole months until the target date
     * @param advice           plain-language guidance, with figures
     */
    public record GoalView(
            Long id,
            String name,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            BigDecimal remainingAmount,
            BigDecimal progressPercent,
            LocalDate targetDate,
            Enums.GoalStatus status,
            String icon,
            BigDecimal requiredMonthly,
            BigDecimal recommendedMonthly,
            boolean onTrack,
            long monthsRemaining,
            String advice
    ) {
        public static GoalView of(SavingsGoal goal, BigDecimal requiredMonthly,
                                  BigDecimal recommendedMonthly, boolean onTrack,
                                  long monthsRemaining, String advice) {
            return new GoalView(
                    goal.getId(), goal.getName(), goal.getTargetAmount(), goal.getSavedAmount(),
                    goal.remainingAmount(), goal.progressPercent(), goal.getTargetDate(),
                    goal.getStatus(), goal.getIcon(), requiredMonthly, recommendedMonthly,
                    onTrack, monthsRemaining, advice);
        }
    }
}
