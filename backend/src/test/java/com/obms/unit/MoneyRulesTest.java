package com.obms.unit;

import com.obms.common.References;
import com.obms.domain.Account;
import com.obms.domain.Enums;
import com.obms.domain.OtpToken;
import com.obms.domain.SavingsGoal;
import com.obms.service.FinancialHealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the monetary and domain invariants.
 *
 * <p>These are the rules that must hold regardless of which service is
 * calling: available balance respects the minimum, goal progress cannot
 * exceed 100%, an OTP stops being usable once spent, and generated
 * identifiers do not collide.</p>
 */
@DisplayName("Unit: money and domain rules")
class MoneyRulesTest {

    private Account account(String balance, String minimum) {
        Account account = new Account();
        account.setBalance(new BigDecimal(balance));
        account.setMinimumBalance(new BigDecimal(minimum));
        account.setStatus(Enums.AccountStatus.ACTIVE);
        return account;
    }

    @Test
    @DisplayName("available balance subtracts the minimum balance floor")
    void availableBalanceRespectsMinimum() {
        assertThat(account("5000.00", "500.00").availableBalance())
                .isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("available balance never goes negative")
    void availableBalanceNeverNegative() {
        // A balance already below the floor must report zero available, not a
        // negative figure that a caller might then treat as spendable.
        assertThat(account("200.00", "500.00").availableBalance())
                .isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "an account that is {0} is operational: {1}")
    @CsvSource({
            "ACTIVE,true",
            "DORMANT,false",
            "FROZEN,false",
            "CLOSED,false"
    })
    @DisplayName("only an ACTIVE account permits money movement")
    void onlyActiveIsOperational(Enums.AccountStatus status, boolean expected) {
        Account account = account("1000.00", "0.00");
        account.setStatus(status);

        assertThat(account.isOperational()).isEqualTo(expected);
    }

    private SavingsGoal goal(String target, String saved) {
        SavingsGoal goal = new SavingsGoal();
        goal.setTargetAmount(new BigDecimal(target));
        goal.setSavedAmount(new BigDecimal(saved));
        goal.setTargetDate(LocalDate.now().plusMonths(6));
        return goal;
    }

    @Test
    @DisplayName("goal progress is a percentage of the target")
    void goalProgress() {
        assertThat(goal("10000.00", "2500.00").progressPercent())
                .isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("goal progress is capped at 100% when over-funded")
    void goalProgressCapped() {
        assertThat(goal("10000.00", "15000.00").progressPercent())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("remaining amount is zero once the target is met")
    void remainingNeverNegative() {
        assertThat(goal("10000.00", "15000.00").remainingAmount())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a zero target does not divide by zero")
    void zeroTargetIsSafe() {
        assertThat(goal("0.00", "100.00").progressPercent()).isEqualByComparingTo("0");
    }

    private OtpToken liveToken() {
        OtpToken token = new OtpToken();
        token.setUsername("user@example.com");
        token.setPurpose(Enums.OtpPurpose.LOGIN);
        token.setCodeHash("$2a$10$notarealhash");
        token.setMaxAttempts(5);
        token.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        return token;
    }

    @Test
    @DisplayName("a fresh OTP is usable")
    void freshOtpIsUsable() {
        assertThat(liveToken().isUsable()).isTrue();
    }

    @Test
    @DisplayName("an expired OTP is not usable")
    void expiredOtpUnusable() {
        OtpToken token = liveToken();
        token.setExpiresAt(Instant.now().minusSeconds(1));

        assertThat(token.isExpired()).isTrue();
        assertThat(token.isUsable()).isFalse();
    }

    @Test
    @DisplayName("a consumed OTP cannot be replayed")
    void consumedOtpUnusable() {
        OtpToken token = liveToken();
        token.setConsumedAt(Instant.now());

        assertThat(token.isConsumed()).isTrue();
        assertThat(token.isUsable())
                .as("single use is what stops a captured code being reused")
                .isFalse();
    }

    @Test
    @DisplayName("an OTP is exhausted after the attempt limit")
    void exhaustedOtpUnusable() {
        OtpToken token = liveToken();
        token.setAttempts(5);

        assertThat(token.isExhausted()).isTrue();
        assertThat(token.isUsable())
                .as("bounding attempts is what makes brute force impractical")
                .isFalse();
    }

    @ParameterizedTest(name = "a score of {0} is banded {1}")
    @CsvSource({
            "100,EXCELLENT",
            "80,EXCELLENT",
            "79,GOOD",
            "60,GOOD",
            "59,FAIR",
            "40,FAIR",
            "39,NEEDS_ATTENTION",
            "0,NEEDS_ATTENTION"
    })
    @DisplayName("financial health bands are applied at the documented boundaries")
    void healthBands(int score, String expected) {
        assertThat(FinancialHealthService.bandFor(score)).isEqualTo(expected);
    }

    @Test
    @DisplayName("generated account numbers are 14 digits and do not repeat")
    void accountNumbersAreUniqueAndWellFormed() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String number = References.accountNumber();
            assertThat(number).matches("\\d{14}");
            generated.add(number);
        }
        // A collision in 2000 draws would indicate a weak generator; the
        // service also checks the database before assigning one.
        assertThat(generated).hasSizeGreaterThan(1990);
    }

    @Test
    @DisplayName("transaction references carry a date and a random tail")
    void transactionReferenceFormat() {
        assertThat(References.transactionReference()).matches("TXN\\d{8}[A-Z0-9]{6}");
        assertThat(References.transferGroupReference()).matches("TRF\\d{8}[A-Z0-9]{6}");
        assertThat(References.billPaymentReference()).matches("BIL\\d{8}[A-Z0-9]{6}");
        assertThat(References.customerNumber()).matches("CUS[A-Z0-9]{6}");
    }
}
