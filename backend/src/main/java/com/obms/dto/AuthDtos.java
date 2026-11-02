package com.obms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request and response shapes for registration, login and 2FA (FR-1, FR-2).
 */
public final class AuthDtos {

    private AuthDtos() {
        // DTO holder
    }

    /**
     * New customer registration.
     *
     * <p>The password rule is enforced here with a regex rather than left to
     * the UI, because client-side validation is a convenience and not a
     * control. Minimum length is 10 with mixed character classes.</p>
     */
    public record RegisterRequest(
            @NotBlank(message = "First name is required")
            @Size(max = 60)
            String firstName,

            @NotBlank(message = "Last name is required")
            @Size(max = 60)
            String lastName,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 160)
            String email,

            @NotBlank(message = "Mobile number is required")
            @Pattern(regexp = "^[6-9]\\d{9}$",
                    message = "Enter a valid 10-digit Indian mobile number")
            String mobile,

            @NotBlank(message = "Password is required")
            @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "Password must include lower case, upper case, a digit and a symbol")
            String password,

            @Size(max = 255)
            String addressLine,

            @Size(max = 60)
            String city,

            @Size(max = 60)
            String state,

            @Pattern(regexp = "^$|^\\d{6}$", message = "Enter a valid 6-digit PIN code")
            String pincode,

            BigDecimal declaredMonthlyIncome
    ) {
    }

    /**
     * Result of registration: the customer exists but must complete KYC and
     * log in. No token is issued here, so registration cannot be used as a
     * way to obtain authority without authenticating.
     */
    public record RegisterResponse(
            String customerNumber,
            String username,
            String accountNumber,
            String message
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required")
            String username,

            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    /**
     * First-factor outcome. When {@code otpRequired} is true the caller holds a
     * pre-auth token that is accepted by nothing but the OTP endpoint.
     *
     * @param otpRequired     whether a second factor is needed
     * @param preAuthToken    short-lived token for the OTP step
     * @param accessToken     full token, only when 2FA was not required
     * @param expiresInSeconds lifetime of the access token
     * @param maskedDestination where the code was sent, e.g. {@code s****@gmail.com}
     */
    public record LoginResponse(
            boolean otpRequired,
            String preAuthToken,
            String accessToken,
            Integer expiresInSeconds,
            String maskedDestination,
            String message
    ) {
    }

    public record VerifyOtpRequest(
            @NotBlank(message = "Enter the verification code")
            @Size(min = 4, max = 8)
            String code
    ) {
    }

    /**
     * Fully authenticated session.
     *
     * @param accessToken      bearer token for subsequent calls
     * @param expiresInSeconds lifetime in seconds
     * @param username         the authenticated login
     * @param displayName      name to show in the UI
     * @param roles            role names, for client-side navigation only
     * @param customerId       owning customer, null for staff
     * @param kycStatus        current KYC state
     */
    public record SessionResponse(
            String accessToken,
            int expiresInSeconds,
            String username,
            String displayName,
            List<String> roles,
            Long customerId,
            String kycStatus
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String username,

            @NotBlank @Size(min = 4, max = 8) String code,

            @NotBlank
            @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "Password must include lower case, upper case, a digit and a symbol")
            String newPassword
    ) {
    }

    /** Simulated KYC submission (FR-1). */
    public record KycSubmitRequest(
            @NotBlank(message = "Select an ID proof type")
            String idProofType,

            @NotBlank(message = "Enter the ID proof number")
            @Size(min = 4, max = 30)
            String idProofNumber,

            @NotBlank(message = "Select an address proof type")
            String addressProofType
    ) {
    }

    public record ProfileResponse(
            String customerNumber,
            String firstName,
            String lastName,
            String email,
            String mobile,
            String addressLine,
            String city,
            String state,
            String pincode,
            BigDecimal declaredMonthlyIncome,
            String kycStatus
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 255) String addressLine,
            @Size(max = 60) String city,
            @Size(max = 60) String state,
            @Pattern(regexp = "^$|^\\d{6}$", message = "Enter a valid 6-digit PIN code") String pincode,
            BigDecimal declaredMonthlyIncome
    ) {
    }
}
