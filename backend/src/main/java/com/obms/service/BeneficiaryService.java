package com.obms.service;

import com.obms.common.ApiException;
import com.obms.config.AppProperties;
import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.dto.BeneficiaryDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.BeneficiaryRepository;
import com.obms.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Beneficiary registration and verification (FR-5).
 *
 * <p>A beneficiary is created in {@code PENDING_VERIFICATION} and becomes
 * payable only after the customer enters an OTP. That gap is the control: an
 * attacker who gets a session token still cannot add a payee and drain the
 * account without also holding the second factor.</p>
 */
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public List<BeneficiaryDtos.BeneficiaryView> list(Long customerId) {
        return beneficiaryRepository
                .findByOwnerCustomerIdAndStatusNot(customerId, Enums.BeneficiaryStatus.REMOVED)
                .stream()
                .map(BeneficiaryDtos.BeneficiaryView::from)
                .toList();
    }

    /**
     * Registers a payee and sends a verification code.
     *
     * @return the created beneficiary, not yet payable
     */
    @Transactional
    public BeneficiaryDtos.BeneficiaryView add(Long customerId, String username,
                                                BeneficiaryDtos.AddBeneficiaryRequest request) {
        Customer owner = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));

        String accountNumber = request.accountNumber().trim();

        if (beneficiaryRepository.existsByOwnerCustomerIdAndAccountNumberAndStatusNot(
                customerId, accountNumber, Enums.BeneficiaryStatus.REMOVED)) {
            throw ApiException.conflict("BENEFICIARY_EXISTS",
                    "This account is already in your beneficiary list.");
        }

        boolean ownAccount = accountRepository.findByAccountNumber(accountNumber)
                .map(account -> account.getCustomer().getId().equals(customerId))
                .orElse(false);
        if (ownAccount) {
            throw ApiException.badRequest("OWN_ACCOUNT",
                    "This is your own account. Use an own-account transfer instead.");
        }

        Account internal = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (internal == null) {
            // Phase-1 scope is transfers within the bank, so a payee that does
            // not resolve internally is rejected at registration rather than
            // being accepted and failing later at transfer time.
            throw ApiException.badRequest("ACCOUNT_NOT_FOUND",
                    "No account with that number exists at " + properties.getBank().getName()
                            + ". Transfers outside the bank are not supported in this release.");
        }

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setOwnerCustomer(owner);
        beneficiary.setBeneficiaryName(request.beneficiaryName().trim());
        beneficiary.setAccountNumber(accountNumber);
        beneficiary.setIfscCode(request.ifscCode().trim().toUpperCase(java.util.Locale.ROOT));
        beneficiary.setNickname(request.nickname());
        beneficiary.setBankName(properties.getBank().getName());
        beneficiary.setInternalAccount(true);
        beneficiary.setStatus(Enums.BeneficiaryStatus.PENDING_VERIFICATION);
        Beneficiary saved = beneficiaryRepository.save(beneficiary);

        String code = otpService.issue(username, Enums.OtpPurpose.BENEFICIARY_VERIFICATION,
                String.valueOf(saved.getId()));
        notificationService.sendEmailQuietly(owner.getEmail(), "Verify your new payee",
                "Your code to verify " + saved.getBeneficiaryName() + " is " + code
                        + ". It expires in 5 minutes.");

        auditService.record("beneficiary.added", "Beneficiary", String.valueOf(saved.getId()),
                "Pending verification for account ending " + tail(accountNumber));

        return BeneficiaryDtos.BeneficiaryView.from(saved);
    }

    /**
     * Activates a beneficiary once the customer proves the second factor.
     */
    @Transactional
    public BeneficiaryDtos.BeneficiaryView verify(Long beneficiaryId, Long customerId,
                                                   String username, String code) {
        Beneficiary beneficiary = beneficiaryRepository
                .findByIdAndOwnerCustomerId(beneficiaryId, customerId)
                .orElseThrow(() -> ApiException.notFound("Beneficiary"));

        if (beneficiary.getStatus() == Enums.BeneficiaryStatus.ACTIVE) {
            return BeneficiaryDtos.BeneficiaryView.from(beneficiary);
        }
        if (beneficiary.getStatus() == Enums.BeneficiaryStatus.REMOVED) {
            throw ApiException.invalidState("This beneficiary has been removed.");
        }

        otpService.verify(username, Enums.OtpPurpose.BENEFICIARY_VERIFICATION,
                String.valueOf(beneficiaryId), code);

        beneficiary.setStatus(Enums.BeneficiaryStatus.ACTIVE);
        beneficiary.setVerifiedAt(Instant.now());

        auditService.record("beneficiary.verified", "Beneficiary", String.valueOf(beneficiaryId),
                "Activated " + beneficiary.getBeneficiaryName());
        notificationService.notify(beneficiary.getOwnerCustomer(),
                Enums.NotificationType.BENEFICIARY_ADDED,
                "New payee added",
                beneficiary.getBeneficiaryName() + " is now verified and can receive transfers. "
                        + "If you did not add this payee, contact support immediately.",
                "/beneficiaries");

        return BeneficiaryDtos.BeneficiaryView.from(beneficiary);
    }

    @Transactional
    public BeneficiaryDtos.BeneficiaryView rename(Long beneficiaryId, Long customerId,
                                                   String nickname) {
        Beneficiary beneficiary = beneficiaryRepository
                .findByIdAndOwnerCustomerId(beneficiaryId, customerId)
                .orElseThrow(() -> ApiException.notFound("Beneficiary"));
        beneficiary.setNickname(nickname);
        return BeneficiaryDtos.BeneficiaryView.from(beneficiary);
    }

    /**
     * Soft-deletes a beneficiary.
     *
     * <p>The row is marked {@code REMOVED} rather than deleted so that past
     * transfers remain explainable and so the audit trail still resolves the
     * payee that was paid.</p>
     */
    @Transactional
    public void remove(Long beneficiaryId, Long customerId) {
        Beneficiary beneficiary = beneficiaryRepository
                .findByIdAndOwnerCustomerId(beneficiaryId, customerId)
                .orElseThrow(() -> ApiException.notFound("Beneficiary"));
        beneficiary.setStatus(Enums.BeneficiaryStatus.REMOVED);
        auditService.record("beneficiary.removed", "Beneficiary", String.valueOf(beneficiaryId),
                "Removed " + beneficiary.getBeneficiaryName());
    }

    private static String tail(String accountNumber) {
        return accountNumber.length() <= 4
                ? accountNumber
                : accountNumber.substring(accountNumber.length() - 4);
    }
}
