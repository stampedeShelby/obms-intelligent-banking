package com.obms.integration;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Enums;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the transfer workflow: ownership, limits, idempotency,
 * beneficiary verification and the risk-based step-up path.
 */
@DisplayName("Integration: transfer workflow")
class TransferServiceTest extends IntegrationTestBase {

    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PendingTransferRepository pendingTransferRepository;
    @Autowired
    private FraudAlertRepository fraudAlertRepository;
    @Autowired
    private TestDataFactory factory;

    private TransferDtos.TransferRequest toBeneficiary(Long sourceId, Long beneficiaryId,
                                                        String amount) {
        return new TransferDtos.TransferRequest(sourceId, beneficiaryId, null,
                new BigDecimal(amount), "Integration test transfer");
    }

    @Test
    @DisplayName("a small transfer to an established payee settles immediately")
    void smallTransferSettles() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("1000.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        TransferDtos.TransferResult result = transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "2500.00"), null);

        assertThat(result.outcome()).isEqualTo(TransferDtos.Outcome.COMPLETED);
        assertThat(result.reference()).isNotBlank();
        assertThat(result.balanceAfter()).isEqualByComparingTo("47500.00");
    }

    @Test
    @DisplayName("a large transfer requires a one-time code and moves no money yet")
    void largeTransferRequiresVerification() {
        var sender = factory.customerWithAccount(new BigDecimal("200000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("1000.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        // The configured always-verify threshold is 25,000.
        TransferDtos.TransferResult result = transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "40000.00"), null);

        assertThat(result.outcome()).isEqualTo(TransferDtos.Outcome.VERIFICATION_REQUIRED);
        assertThat(result.pendingRef()).isNotBlank();
        assertThat(result.reference()).isNull();

        Account source = accountRepository.findById(sender.account().getId()).orElseThrow();
        assertThat(source.getBalance())
                .as("no money may move until the second factor is verified")
                .isEqualByComparingTo("200000.00");

        assertThat(pendingTransferRepository.findByPendingRef(result.pendingRef()))
                .isPresent();
    }

    @Test
    @DisplayName("confirming with a wrong code is rejected and leaves the balance alone")
    void wrongOtpRejected() {
        var sender = factory.customerWithAccount(new BigDecimal("200000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("1000.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        TransferDtos.TransferResult pending = transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "40000.00"), null);

        assertThatThrownBy(() -> transferService.confirm(
                sender.customer().getId(), sender.username(), pending.pendingRef(), "000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("incorrect or has expired");

        assertThat(accountRepository.findById(sender.account().getId()).orElseThrow()
                .getBalance()).isEqualByComparingTo("200000.00");
    }

    @Test
    @DisplayName("a transfer to an unverified payee is refused")
    void unverifiedBeneficiaryRefused() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("1000.00"));
        var payee = factory.unverifiedBeneficiary(sender.customer(), receiver.account());

        assertThatThrownBy(() -> transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "1000.00"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    @DisplayName("a customer cannot send from an account they do not own")
    void cannotSendFromAnotherCustomersAccount() {
        var attacker = factory.customerWithAccount(new BigDecimal("100.00"));
        var victim = factory.customerWithAccount(new BigDecimal("500000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(attacker.customer(), receiver.account(), 90);

        // The attacker supplies the victim's account id in the request body.
        // Ownership is checked against the token's customer id, so this fails.
        assertThatThrownBy(() -> transferService.initiate(
                attacker.customer().getId(), attacker.username(),
                toBeneficiary(victim.account().getId(), payee.getId(), "1000.00"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("do not have access");

        assertThat(accountRepository.findById(victim.account().getId()).orElseThrow()
                .getBalance()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("a customer cannot use another customer's beneficiary")
    void cannotUseAnotherCustomersBeneficiary() {
        var attacker = factory.customerWithAccount(new BigDecimal("50000.00"));
        var other = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var othersPayee = factory.verifiedBeneficiary(other.customer(), receiver.account(), 90);

        assertThatThrownBy(() -> transferService.initiate(
                attacker.customer().getId(), attacker.username(),
                toBeneficiary(attacker.account().getId(), othersPayee.getId(), "1000.00"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("a transfer above the available balance is refused")
    void insufficientBalanceRefused() {
        var sender = factory.customerWithAccount(new BigDecimal("2000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        assertThatThrownBy(() -> transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "5000.00"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("enough available balance");
    }

    @Test
    @DisplayName("the daily transfer limit is enforced across several transfers")
    void dailyLimitEnforced() {
        var sender = factory.customerWithAccount(new BigDecimal("500000.00"),
                BigDecimal.ZERO, "CUSTOMER");
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        // Limit is 100,000 a day. Five transfers of 20,000 each exhaust it;
        // each is below the 25,000 step-up threshold so they settle directly.
        for (int i = 0; i < 5; i++) {
            TransferDtos.TransferResult result = transferService.initiate(
                    sender.customer().getId(), sender.username(),
                    toBeneficiary(sender.account().getId(), payee.getId(), "20000.00"), null);
            assertThat(result.outcome()).isEqualTo(TransferDtos.Outcome.COMPLETED);
        }

        assertThatThrownBy(() -> transferService.initiate(
                sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "1000.00"), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("daily limit");
    }

    @Test
    @DisplayName("replaying an idempotency key returns the original transfer, not a second debit")
    void idempotencyPreventsDoubleDebit() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        String key = UUID.randomUUID().toString();
        var request = toBeneficiary(sender.account().getId(), payee.getId(), "3000.00");

        TransferDtos.TransferResult first = transferService.initiate(
                sender.customer().getId(), sender.username(), request, key);
        TransferDtos.TransferResult replay = transferService.initiate(
                sender.customer().getId(), sender.username(), request, key);

        assertThat(replay.reference())
                .as("the replay must return the original reference")
                .isEqualTo(first.reference());

        assertThat(accountRepository.findById(sender.account().getId()).orElseThrow()
                .getBalance())
                .as("the account must be debited exactly once")
                .isEqualByComparingTo("47000.00");
    }

    @Test
    @DisplayName("different idempotency keys allow genuine repeat payments")
    void differentKeysAllowRepeats() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        var request = toBeneficiary(sender.account().getId(), payee.getId(), "3000.00");
        transferService.initiate(sender.customer().getId(), sender.username(), request,
                UUID.randomUUID().toString());
        transferService.initiate(sender.customer().getId(), sender.username(), request,
                UUID.randomUUID().toString());

        assertThat(accountRepository.findById(sender.account().getId()).orElseThrow()
                .getBalance())
                .as("a legitimate repeat payment must not be blocked")
                .isEqualByComparingTo("44000.00");
    }

    @Test
    @DisplayName("an own-account transfer between the customer's own accounts works")
    void ownAccountTransfer() {
        var customer = factory.customerWithAccount(new BigDecimal("20000.00"));
        Account current = factory.extraAccount(customer.customer(),
                Enums.AccountType.CURRENT, new BigDecimal("1000.00"));

        TransferDtos.TransferResult result = transferService.initiate(
                customer.customer().getId(), customer.username(),
                new TransferDtos.TransferRequest(customer.account().getId(), null,
                        current.getId(), new BigDecimal("5000.00"), "Move to current"),
                null);

        assertThat(result.outcome()).isEqualTo(TransferDtos.Outcome.COMPLETED);
        assertThat(accountRepository.findById(current.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("a transfer to another customer requires a beneficiary, not a raw account id")
    void rawAccountIdToAnotherCustomerRefused() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var other = factory.customerWithAccount(new BigDecimal("1000.00"));

        assertThatThrownBy(() -> transferService.initiate(
                sender.customer().getId(), sender.username(),
                new TransferDtos.TransferRequest(sender.account().getId(), null,
                        other.account().getId(), new BigDecimal("1000.00"), "Direct"),
                null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verified beneficiary");
    }

    @Test
    @DisplayName("a transfer with no destination is rejected")
    void destinationRequired() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));

        assertThatThrownBy(() -> transferService.initiate(
                sender.customer().getId(), sender.username(),
                new TransferDtos.TransferRequest(sender.account().getId(), null, null,
                        new BigDecimal("1000.00"), "Nowhere"),
                null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("destination");
    }

    @Test
    @DisplayName("every settled transfer leaves an explainable risk record")
    void riskAssessmentIsRecorded() {
        var sender = factory.customerWithAccount(new BigDecimal("50000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 90);

        transferService.initiate(sender.customer().getId(), sender.username(),
                toBeneficiary(sender.account().getId(), payee.getId(), "1500.00"), null);

        var alerts = fraudAlertRepository.findByCustomerIdOrderByCreatedAtDesc(
                sender.customer().getId(), PageRequest.of(0, 10));

        assertThat(alerts.getContent()).isNotEmpty();
        var alert = alerts.getContent().get(0);
        assertThat(alert.getPrimaryReason())
                .as("an alert must always carry a reason, never a bare score")
                .isNotBlank();
        assertThat(alert.getRiskScore()).isBetween(0, 100);
        assertThat(alert.getModelVersion()).isNotBlank();
    }
}
