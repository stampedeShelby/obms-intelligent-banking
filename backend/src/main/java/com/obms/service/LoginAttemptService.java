package com.obms.service;

import com.obms.config.AppProperties;
import com.obms.domain.UserAccount;
import com.obms.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Records login outcomes and enforces the lockout threshold.
 *
 * <h2>Why this is a separate bean</h2>
 * <p>This logic used to live in {@code AuthService.login}, which is
 * {@code @Transactional}. It incremented the counter on the managed entity and
 * then threw {@code ApiException} to reject the attempt - so Spring rolled the
 * transaction back and discarded the increment with it. The counter sat at zero
 * forever and the lockout could never engage, leaving password guessing
 * effectively unlimited. A QA pass caught it: six wrong passwords followed by
 * the right one still signed in.</p>
 *
 * <p>Each method therefore runs in {@code REQUIRES_NEW}, so the record of the
 * attempt commits independently of the caller's rollback. This is the same
 * reasoning {@link AuditService} already applied to the audit trail.</p>
 *
 * <p>A private method on {@code AuthService} could not have fixed it: Spring's
 * transaction advice is applied by a proxy, so a self-call would have run
 * inside the caller's transaction regardless of its annotation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserAccountRepository userRepository;
    private final AuditService auditService;
    private final AppProperties properties;

    /**
     * Counts a failed attempt and locks the account at the threshold.
     *
     * <p>Loads the user fresh rather than accepting the caller's entity,
     * because that entity belongs to a transaction which is about to roll
     * back.</p>
     *
     * @param username the login that was attempted
     * @param ipAddress caller address for the audit trail
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username, String ipAddress) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            int max = properties.getSecurity().getMaxFailedLogins();
            user.setFailedLoginAttempts(attempts);

            if (attempts >= max) {
                user.setLockedUntil(Instant.now()
                        .plus(properties.getSecurity().getLockoutMinutes(), ChronoUnit.MINUTES));
                log.warn("Locked account {} after {} failed attempts", username, attempts);
                auditService.recordAs(username, "auth.account.locked", false,
                        "Locked after " + attempts + " failed attempts", ipAddress);
            } else {
                auditService.recordAs(username, "auth.login.failed", false,
                        "Failed attempt " + attempts + " of " + max, ipAddress);
            }
            userRepository.save(user);
        });
    }

    /**
     * Clears the counter after a successful password check, so a user who
     * mistypes twice and then succeeds does not creep toward a lockout.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }

    /**
     * @return true when a lockout is currently in force for this user
     */
    @Transactional(readOnly = true)
    public boolean isLocked(String username) {
        return userRepository.findByUsername(username)
                .map(UserAccount::isLocked)
                .orElse(false);
    }
}
