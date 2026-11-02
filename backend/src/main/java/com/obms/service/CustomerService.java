package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import com.obms.dto.AuthDtos;
import com.obms.repository.CustomerRepository;
import com.obms.repository.KycRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Customer profile and simulated KYC submission (FR-1).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final KycRepository kycRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AuthDtos.ProfileResponse profile(Long customerId) {
        Customer customer = require(customerId);
        String kycStatus = kycRepository.findByCustomerId(customerId)
                .map(kyc -> kyc.getStatus().name())
                .orElse(Enums.KycStatus.NOT_SUBMITTED.name());

        return new AuthDtos.ProfileResponse(
                customer.getCustomerNumber(), customer.getFirstName(), customer.getLastName(),
                customer.getEmail(), customer.getMobile(), customer.getAddressLine(),
                customer.getCity(), customer.getState(), customer.getPincode(),
                customer.getDeclaredMonthlyIncome(), kycStatus);
    }

    /**
     * Updates the mutable parts of a profile.
     *
     * <p>Email and mobile are deliberately not editable here: changing either
     * changes where second-factor codes are delivered, which is a security
     * event that needs its own verified flow rather than a profile form.</p>
     */
    @Transactional
    public AuthDtos.ProfileResponse updateProfile(Long customerId,
                                                   AuthDtos.UpdateProfileRequest request) {
        Customer customer = require(customerId);
        if (request.addressLine() != null) {
            customer.setAddressLine(request.addressLine());
        }
        if (request.city() != null) {
            customer.setCity(request.city());
        }
        if (request.state() != null) {
            customer.setState(request.state());
        }
        if (request.pincode() != null && !request.pincode().isBlank()) {
            customer.setPincode(request.pincode());
        }
        if (request.declaredMonthlyIncome() != null) {
            if (request.declaredMonthlyIncome().signum() < 0) {
                throw ApiException.badRequest("INVALID_INCOME", "Income cannot be negative.");
            }
            customer.setDeclaredMonthlyIncome(request.declaredMonthlyIncome());
        }
        auditService.record("customer.profile.updated", "Customer",
                customer.getCustomerNumber(), "Profile fields updated");
        return profile(customerId);
    }

    /**
     * Submits KYC documents for review.
     *
     * <p>Only a masked tail of the identifier is retained. The full document
     * number is used to build the mask and then discarded, so a database dump
     * does not expose customers' government identifiers.</p>
     */
    @Transactional
    public String submitKyc(Long customerId, AuthDtos.KycSubmitRequest request) {
        Customer customer = require(customerId);
        Kyc kyc = kycRepository.findByCustomerId(customerId).orElseGet(() -> {
            Kyc fresh = new Kyc();
            fresh.setCustomer(customer);
            return kycRepository.save(fresh);
        });

        if (kyc.getStatus() == Enums.KycStatus.VERIFIED) {
            throw ApiException.invalidState("KYC is already verified.");
        }

        kyc.setIdProofType(request.idProofType());
        kyc.setIdProofMasked(mask(request.idProofNumber()));
        kyc.setAddressProofType(request.addressProofType());
        kyc.setStatus(Enums.KycStatus.PENDING);
        kyc.setSubmittedAt(Instant.now());
        kyc.setRejectionReason(null);

        auditService.record("kyc.submitted", "Customer", customer.getCustomerNumber(),
                "KYC submitted with " + request.idProofType());
        notificationService.notify(customer, Enums.NotificationType.SECURITY,
                "KYC submitted",
                "Your documents are under review. You will be notified once verification completes.",
                "/profile");

        return kyc.getStatus().name();
    }

    private Customer require(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));
    }

    /**
     * @return the identifier with all but the last four characters replaced
     */
    static String mask(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
