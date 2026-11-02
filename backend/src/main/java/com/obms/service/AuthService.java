package com.obms.service;

import com.obms.common.ApiException;
import com.obms.common.References;
import com.obms.config.AppProperties;
import com.obms.domain.Account;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.Kyc;
import com.obms.domain.Role;
import com.obms.domain.UserAccount;
import com.obms.dto.AuthDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.KycRepository;
import com.obms.repository.RoleRepository;
import com.obms.repository.UserAccountRepository;
import com.obms.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Registration, login, two-factor verification and password reset.
 *
 * <p>Several behaviours here are deliberate security choices rather than
 * incidental:</p>
 * <ul>
 *   <li><b>Uniform failure.</b> An unknown username and a wrong password
 *       produce the same error. Distinguishing them would let an attacker
 *       enumerate valid accounts.</li>
 *   <li><b>Lockout.</b> Five consecutive failures lock the account for fifteen
 *       minutes, which turns online password guessing from cheap into
 *       impractical. The counter resets on success.</li>
 *   <li><b>Two tokens, two meanings.</b> The password step yields a pre-auth
 *       token with no authorities. Only OTP verification yields an access
 *       token. Skipping the second factor is therefore not possible by
 *       replaying the first response.</li>
 *   <li><b>Silent password reset.</b> {@code forgotPassword} returns the same
 *       response whether or not the email exists, so it cannot be used to test
 *       which addresses are registered.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final UserAccountRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final KycRepository kycRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties properties;

    // ------------------------------------------------------------------
    // registration
    // ------------------------------------------------------------------

    /**
     * Registers a customer, opens their first savings account, and creates a
     * KYC record awaiting submission (FR-1).
     */
    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);

        if (customerRepository.existsByEmail(email) || userRepository.existsByUsername(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists.");
        }
        if (customerRepository.existsByMobile(request.mobile())) {
            throw ApiException.conflict("MOBILE_IN_USE",
                    "An account with this mobile number already exists.");
        }

        Customer customer = new Customer();
        customer.setCustomerNumber(References.customerNumber());
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(email);
        customer.setMobile(request.mobile());
        customer.setAddressLine(request.addressLine());
        customer.setCity(request.city());
        customer.setState(request.state());
        customer.setPincode(blankToNull(request.pincode()));
        customer.setDeclaredMonthlyIncome(request.declaredMonthlyIncome());
        Customer savedCustomer = customerRepository.save(customer);

        Role customerRole = roleRepository.findByName(ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "Role CUSTOMER is missing. Reference data was not seeded."));

        UserAccount user = new UserAccount();
        user.setUsername(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCustomer(savedCustomer);
        user.setRoles(java.util.Set.of(customerRole));
        userRepository.save(user);

        Kyc kyc = new Kyc();
        kyc.setCustomer(savedCustomer);
        kyc.setStatus(Enums.KycStatus.NOT_SUBMITTED);
        kycRepository.save(kyc);

        Account account = openAccount(savedCustomer, Enums.AccountType.SAVINGS, BigDecimal.ZERO);

        auditService.recordAs(email, "auth.registered", true,
                "Customer " + savedCustomer.getCustomerNumber() + " registered", null);
        notificationService.notify(savedCustomer, Enums.NotificationType.SECURITY,
                "Welcome to OBMS",
                "Your account " + account.getAccountNumber()
                        + " is open. Complete KYC to unlock transfers.",
                "/profile");

        return new AuthDtos.RegisterResponse(savedCustomer.getCustomerNumber(), email,
                account.getAccountNumber(),
                "Registration successful. Complete KYC verification to start transacting.");
    }

    /**
     * Opens an account for a customer.
     *
     * @param openingBalance initial credit; used by demo seeding, zero in
     *                       ordinary registration
     */
    @Transactional
    public Account openAccount(Customer customer, Enums.AccountType type, BigDecimal openingBalance) {
        String accountNumber;
        do {
            accountNumber = References.accountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setIfscCode(properties.getBank().getIfsc());
        account.setCustomer(customer);
        account.setAccountType(type);
        account.setBalance(openingBalance == null ? BigDecimal.ZERO : openingBalance);
        account.setMinimumBalance(type == Enums.AccountType.SAVINGS
                ? new BigDecimal("500.00") : BigDecimal.ZERO);
        account.setDailyTransferLimit(properties.getTransfer().getDefaultDailyLimit());
        account.setNickname(type == Enums.AccountType.SAVINGS ? "Savings" : "Current");
        return accountRepository.save(account);
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    /**
     * Verifies the password factor and issues an OTP challenge (FR-2).
     *
     * @param ipAddress caller address, recorded in the audit trail
     */
    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String ipAddress) {
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    auditService.recordAs(username, "auth.login.failed", false,
                            "Unknown username", ipAddress);
                    // Same error as a wrong password: no account enumeration.
                    return ApiException.invalidCredentials();
                });

        if (user.isLocked()) {
            auditService.recordAs(username, "auth.login.locked", false,
                    "Attempt while locked", ipAddress);
            throw ApiException.unauthorized(
                    "This account is temporarily locked after repeated failed attempts. "
                            + "Try again later or reset your password.");
        }
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("This account is disabled. Contact support.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, ipAddress);
            throw ApiException.invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        if (!user.isTwoFactorEnabled()) {
            return new AuthDtos.LoginResponse(false, null,
                    issueAccessToken(user), jwtService.accessTokenSeconds(), null,
                    "Signed in.");
        }

        String code = otpService.issue(username, Enums.OtpPurpose.LOGIN, null);
        String destination = user.getCustomer() != null ? user.getCustomer().getEmail() : username;
        notificationService.sendEmailQuietly(destination, "Your OBMS sign-in code",
                "Your one-time code is " + code + ". It expires in 5 minutes.");

        auditService.recordAs(username, "auth.password.verified", true,
                "Password factor passed, OTP issued", ipAddress);

        return new AuthDtos.LoginResponse(true, jwtService.issuePreAuthToken(username),
                null, null, maskEmail(destination),
                "A verification code has been sent. Enter it to finish signing in.");
    }

    /**
     * Completes login by verifying the OTP against a pre-auth token.
     *
     * @param preAuthToken token returned by {@link #login}
     * @param code         the code the customer received
     */
    @Transactional
    public AuthDtos.SessionResponse verifyLoginOtp(String preAuthToken, String code, String ipAddress) {
        Claims claims = jwtService.parse(preAuthToken)
                .orElseThrow(() -> ApiException.unauthorized(
                        "This sign-in attempt has expired. Start again."));

        if (!JwtService.TYPE_PRE_AUTH.equals(jwtService.tokenTypeOf(claims))) {
            throw ApiException.unauthorized("Invalid verification request.");
        }

        String username = claims.getSubject();
        otpService.verify(username, Enums.OtpPurpose.LOGIN, null, code);

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists."));

        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ipAddress);
        user.setFailedLoginAttempts(0);

        auditService.recordAs(username, "auth.login.success", true, "2FA completed", ipAddress);

        if (user.getCustomer() != null) {
            notificationService.notify(user.getCustomer(), Enums.NotificationType.LOGIN_ALERT,
                    "New sign-in to your account",
                    "A sign-in was completed at " + Instant.now()
                            + (ipAddress == null ? "" : " from " + ipAddress)
                            + ". If this was not you, reset your password immediately.",
                    "/profile");
        }

        return buildSession(user);
    }

    /** Re-issues a login OTP for an in-flight attempt. */
    @Transactional
    public void resendLoginOtp(String preAuthToken) {
        Claims claims = jwtService.parse(preAuthToken)
                .orElseThrow(() -> ApiException.unauthorized(
                        "This sign-in attempt has expired. Start again."));
        if (!JwtService.TYPE_PRE_AUTH.equals(jwtService.tokenTypeOf(claims))) {
            throw ApiException.unauthorized("Invalid verification request.");
        }
        String username = claims.getSubject();
        String code = otpService.issue(username, Enums.OtpPurpose.LOGIN, null);
        notificationService.sendEmailQuietly(username, "Your OBMS sign-in code",
                "Your one-time code is " + code + ". It expires in 5 minutes.");
    }

    // ------------------------------------------------------------------
    // password reset
    // ------------------------------------------------------------------

    /**
     * Starts a password reset.
     *
     * <p>Always reports success. If the address is not registered, no email is
     * sent and no token is created, but the response is identical - otherwise
     * this endpoint would reveal which addresses hold accounts.</p>
     */
    @Transactional
    public void forgotPassword(String email) {
        String normalised = email.trim().toLowerCase(java.util.Locale.ROOT);
        userRepository.findByUsername(normalised).ifPresent(user -> {
            String code = otpService.issue(normalised, Enums.OtpPurpose.PASSWORD_RESET, null);
            notificationService.sendEmailQuietly(normalised, "Reset your OBMS password",
                    "Your password reset code is " + code + ". It expires in 5 minutes. "
                            + "If you did not request this, you can ignore this message.");
            auditService.recordAs(normalised, "auth.password_reset.requested", true, null, null);
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        otpService.verify(username, Enums.OtpPurpose.PASSWORD_RESET, null, request.code());

        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("Invalid reset request."));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        auditService.recordAs(username, "auth.password_reset.completed", true, null, null);
        if (user.getCustomer() != null) {
            notificationService.notify(user.getCustomer(), Enums.NotificationType.SECURITY,
                    "Your password was changed",
                    "Your OBMS password was changed. If this was not you, contact support now.",
                    "/profile");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void registerFailedAttempt(UserAccount user, String ipAddress) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        int max = properties.getSecurity().getMaxFailedLogins();
        if (attempts >= max) {
            user.setLockedUntil(Instant.now()
                    .plus(properties.getSecurity().getLockoutMinutes(), ChronoUnit.MINUTES));
            log.warn("Locked account {} after {} failed attempts", user.getUsername(), attempts);
            auditService.recordAs(user.getUsername(), "auth.account.locked", false,
                    "Locked after " + attempts + " failed attempts", ipAddress);
        } else {
            auditService.recordAs(user.getUsername(), "auth.login.failed", false,
                    "Failed attempt " + attempts + " of " + max, ipAddress);
        }
    }

    private String issueAccessToken(UserAccount user) {
        return jwtService.issueAccessToken(user.getUsername(), roleNames(user),
                user.getCustomer() == null ? null : user.getCustomer().getId());
    }

    public AuthDtos.SessionResponse buildSession(UserAccount user) {
        Customer customer = user.getCustomer();
        String kycStatus = customer == null
                ? null
                : kycRepository.findByCustomerId(customer.getId())
                        .map(kyc -> kyc.getStatus().name())
                        .orElse(Enums.KycStatus.NOT_SUBMITTED.name());

        return new AuthDtos.SessionResponse(
                issueAccessToken(user),
                jwtService.accessTokenSeconds(),
                user.getUsername(),
                customer == null ? user.getUsername() : customer.fullName(),
                roleNames(user),
                customer == null ? null : customer.getId(),
                kycStatus);
    }

    private List<String> roleNames(UserAccount user) {
        return user.getRoles().stream().map(Role::getName).sorted().toList();
    }

    /**
     * @return the address with the local part reduced to its first character
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        return email.charAt(0) + "****" + email.substring(at);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
