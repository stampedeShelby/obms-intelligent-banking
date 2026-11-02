package com.obms.dto;

import com.obms.domain.Beneficiary;
import com.obms.domain.Enums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Beneficiary request and response shapes (FR-5). */
public final class BeneficiaryDtos {

    private BeneficiaryDtos() {
        // DTO holder
    }

    /**
     * Payee registration.
     *
     * <p>The IFSC pattern is the Reserve Bank format: four letters, a zero,
     * then six alphanumerics. Validating it here means a typo is caught before
     * a transfer is ever attempted.</p>
     */
    public record AddBeneficiaryRequest(
            @NotBlank(message = "Beneficiary name is required")
            @Size(max = 120)
            String beneficiaryName,

            @NotBlank(message = "Account number is required")
            @Pattern(regexp = "^\\d{9,18}$",
                    message = "Account number must be 9 to 18 digits")
            String accountNumber,

            @NotBlank(message = "IFSC code is required")
            @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$",
                    message = "Enter a valid IFSC code, for example OBMS0000001")
            String ifscCode,

            @Size(max = 60)
            String nickname
    ) {
    }

    public record VerifyBeneficiaryRequest(
            @NotBlank(message = "Enter the verification code")
            @Size(min = 4, max = 8)
            String code
    ) {
    }

    public record RenameRequest(
            @Size(max = 60) String nickname
    ) {
    }

    /**
     * Payee as shown to the customer.
     *
     * <p>The account number is masked. A verified payee list is a useful target
     * for anyone who gets a look at the screen, and the customer does not need
     * the full number to choose from their own list.</p>
     */
    public record BeneficiaryView(
            Long id,
            String beneficiaryName,
            String nickname,
            String maskedAccountNumber,
            String ifscCode,
            String bankName,
            Enums.BeneficiaryStatus status,
            boolean payable,
            Instant verifiedAt
    ) {
        public static BeneficiaryView from(Beneficiary beneficiary) {
            return new BeneficiaryView(
                    beneficiary.getId(),
                    beneficiary.getBeneficiaryName(),
                    beneficiary.getNickname(),
                    mask(beneficiary.getAccountNumber()),
                    beneficiary.getIfscCode(),
                    beneficiary.getBankName(),
                    beneficiary.getStatus(),
                    beneficiary.getStatus() == Enums.BeneficiaryStatus.ACTIVE,
                    beneficiary.getVerifiedAt());
        }

        static String mask(String accountNumber) {
            if (accountNumber == null || accountNumber.length() <= 4) {
                return accountNumber;
            }
            return "XXXX" + accountNumber.substring(accountNumber.length() - 4);
        }
    }
}
