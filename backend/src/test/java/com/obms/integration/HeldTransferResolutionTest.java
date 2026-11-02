package com.obms.integration;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Enums;
import com.obms.domain.FraudAlert;
import com.obms.domain.PendingTransfer;
import com.obms.dto.TransferDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.FraudAlertRepository;
import com.obms.repository.PendingTransferRepository;
import com.obms.service.TransferService;
import com.obms.support.IntegrationTestBase;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the resolution of a transfer that fraud controls held for review.
 *
 * <p>This path had no implementation and no test. A held transfer created a
 * {@code PendingTransfer} and moved no money, which is correct, but then had
 * nowhere to go: marking the alert a false positive only touched the alert
 * row, {@code confirm} accepts only {@code AWAITING_VERIFICATION}, and the
 * expiry query deliberately ignores held rows. The customer's money stayed
 * safe while their transfer silently never happened and no outcome was
 * recorded anywhere. It surfaced during a live demonstration, where a held
 * transfer was cleared as a false positive and the amount was never
 * debited.</p>
 *
 * <p>A hold cannot be produced through the public API under the test profile,
 * because fraud scoring is disabled there and the degraded fallback never
 * returns HOLD. These tests therefore build the held state directly, which is
 * the state the fraud workflow puts the system into.</p>
 */
class HeldTransferResolutionTest extends IntegrationTestBase {

    private static final String ANALYST = "analyst@test.obms";

    @Autowired
    private TransferService transferService;

    @Autowired
    private PendingTransferRepository pendingTransferRepository;

    @Autowired
    private FraudAlertRepository fraudAlertRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestDataFactory factory;

    /** Builds the exact state a HOLD decision leaves behind. */
    private Held givenHeldTransfer(BigDecimal sourceBalance, BigDecimal amount) {
        TestDataFactory.Fixture payer = factory.customerWithAccount(sourceBalance);
        TestDataFactory.Fixture payee = factory.customerWithAccount(new BigDecimal("1000.00"));
        Beneficiary beneficiary = factory.verifiedBeneficiary(
                payer.customer(), payee.account(), 0);

        PendingTransfer pending = new PendingTransfer();
        pending.setPendingRef("PND" + System.nanoTime() % 100_000_000L);
        pending.setCustomer(payer.customer());
        pending.setSourceAccount(payer.account());
        pending.setDestinationAccount(payee.account());
        pending.setBeneficiary(beneficiary);
        pending.setAmount(amount);
        pending.setDescription("Rent share");
        pending.setStatus(Enums.TransactionStatus.PENDING);
        pending.setRiskScore(78);
        pending.setRiskLevel(Enums.RiskLevel.HIGH);
        pending.setRiskDecision("HOLD");
        pending.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        PendingTransfer savedPending = pendingTransferRepository.saveAndFlush(pending);

        FraudAlert alert = new FraudAlert();
        alert.setCustomer(payer.customer());
        alert.setEvaluatedRef(savedPending.getPendingRef());
        alert.setAmount(amount);
        alert.setRiskScore(78);
        alert.setRiskLevel(Enums.RiskLevel.HIGH);
        alert.setPrimaryReason("Amount well above this customer's usual transfer");
        alert.setDecision("HOLD");
        alert.setStatus(Enums.AlertStatus.OPEN);
        FraudAlert savedAlert = fraudAlertRepository.saveAndFlush(alert);

        return new Held(savedPending.getPendingRef(), payer.account().getId(),
                payee.account().getId(), savedAlert.getId(), amount);
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    @Test
    @DisplayName("releasing a held transfer settles it and moves the money exactly once")
    void releaseSettlesTheTransfer() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));
        BigDecimal sourceBefore = balanceOf(held.sourceId());
        BigDecimal destBefore = balanceOf(held.destId());

        TransferDtos.TransferResult result =
                transferService.releaseHeld(held.pendingRef(), ANALYST, "Customer confirmed by phone");

        assertThat(result.outcome()).isEqualTo(TransferDtos.Outcome.COMPLETED);
        assertThat(result.reference()).isNotBlank();

        assertThat(balanceOf(held.sourceId()))
                .as("source debited by exactly the held amount")
                .isEqualByComparingTo(sourceBefore.subtract(held.amount()));
        assertThat(balanceOf(held.destId()))
                .as("destination credited by exactly the held amount")
                .isEqualByComparingTo(destBefore.add(held.amount()));

        PendingTransfer after = pendingTransferRepository.findByPendingRef(held.pendingRef()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(Enums.TransactionStatus.COMPLETED);
        assertThat(after.getResultingReference()).isEqualTo(result.reference());

        assertThat(fraudAlertRepository.findById(held.alertId()).orElseThrow())
                .satisfies(alert -> {
                    assertThat(alert.getStatus()).isEqualTo(Enums.AlertStatus.FALSE_POSITIVE);
                    assertThat(alert.isMarkedFalsePositive()).isTrue();
                    assertThat(alert.getReviewedBy()).isEqualTo(ANALYST);
                });
    }

    @Test
    @DisplayName("releasing twice does not debit the customer twice")
    void releaseIsIdempotent() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));

        TransferDtos.TransferResult first =
                transferService.releaseHeld(held.pendingRef(), ANALYST, null);
        BigDecimal afterFirst = balanceOf(held.sourceId());

        TransferDtos.TransferResult second =
                transferService.releaseHeld(held.pendingRef(), ANALYST, null);

        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(balanceOf(held.sourceId()))
                .as("a repeated release must not move money again")
                .isEqualByComparingTo(afterFirst);
    }

    @Test
    @DisplayName("rejecting a held transfer moves no money and records the outcome")
    void rejectMovesNoMoney() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));
        BigDecimal sourceBefore = balanceOf(held.sourceId());
        BigDecimal destBefore = balanceOf(held.destId());

        transferService.rejectHeld(held.pendingRef(), ANALYST, "Customer did not recognise payee");

        assertThat(balanceOf(held.sourceId())).isEqualByComparingTo(sourceBefore);
        assertThat(balanceOf(held.destId())).isEqualByComparingTo(destBefore);

        assertThat(pendingTransferRepository.findByPendingRef(held.pendingRef()).orElseThrow()
                .getStatus())
                .as("the outcome is recorded rather than left open")
                .isEqualTo(Enums.TransactionStatus.FAILED);

        assertThat(fraudAlertRepository.findById(held.alertId()).orElseThrow().getStatus())
                .isEqualTo(Enums.AlertStatus.CONFIRMED_FRAUD);
    }

    @Test
    @DisplayName("release re-checks the balance instead of trusting figures from when it was held")
    void releaseRefusesWhenFundsNoLongerAvailable() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));

        // The customer spends the money while the transfer sits under review.
        Account source = accountRepository.findById(held.sourceId()).orElseThrow();
        source.setBalance(new BigDecimal("1000.00"));
        accountRepository.saveAndFlush(source);

        assertThatThrownBy(() -> transferService.releaseHeld(held.pendingRef(), ANALYST, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("available balance");

        assertThat(balanceOf(held.sourceId()))
                .as("a refused release must not overdraw the account")
                .isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(pendingTransferRepository.findByPendingRef(held.pendingRef()).orElseThrow()
                .getStatus())
                .isEqualTo(Enums.TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("a rejected transfer cannot later be released")
    void rejectedTransferCannotBeReleased() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));
        transferService.rejectHeld(held.pendingRef(), ANALYST, null);

        assertThatThrownBy(() -> transferService.releaseHeld(held.pendingRef(), ANALYST, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot be released");
    }

    @Test
    @DisplayName("the expiry job fails abandoned step-ups but never a transfer held for review")
    void expiryLeavesHeldTransfersAlone() {
        Held held = givenHeldTransfer(new BigDecimal("80000.00"), new BigDecimal("60000.00"));

        PendingTransfer heldRow = pendingTransferRepository.findByPendingRef(held.pendingRef()).orElseThrow();
        heldRow.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        pendingTransferRepository.saveAndFlush(heldRow);

        transferService.expireAbandonedStepUps();

        assertThat(pendingTransferRepository.findByPendingRef(held.pendingRef()).orElseThrow()
                .getStatus())
                .as("a hold awaiting review must not time out on the OTP window")
                .isEqualTo(Enums.TransactionStatus.PENDING);
    }

    private record Held(String pendingRef, Long sourceId, Long destId,
                        Long alertId, BigDecimal amount) {
    }
}
