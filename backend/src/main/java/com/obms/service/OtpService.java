package com.obms.service;

import com.obms.common.ApiException;
import com.obms.config.AppProperties;
import com.obms.domain.Enums;
import com.obms.domain.OtpToken;
import com.obms.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Issues and verifies one-time passwords (FR-2).
 *
 * <p>The security properties this class is responsible for:</p>
 * <ul>
 *   <li><b>Never stored in plaintext.</b> The generated code is hashed with
 *       BCrypt before persistence and the plaintext is returned once, to the
 *       delivery channel only.</li>
 *   <li><b>Never returned by an API.</b> No customer-facing endpoint echoes an
 *       OTP. {@code obms.otp.expose-in-logs} can print it for a local
 *       demonstration and defaults to false.</li>
 *   <li><b>Single use and bounded.</b> A token is consumed on first successful
 *       verification, expires after five minutes, and is exhausted after five
 *       failed attempts, which bounds brute force to a 1-in-200,000 chance
 *       rather than letting an attacker walk the whole six-digit space.</li>
 *   <li><b>Bound to one operation.</b> Purpose plus target reference mean an
 *       OTP issued for login cannot be replayed to approve a transfer.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    /**
     * Issues a fresh OTP, superseding any outstanding one for the same purpose.
     *
     * @return the plaintext code, for delivery to the customer only
     */
    @Transactional
    public String issue(String username, Enums.OtpPurpose purpose, String targetRef) {
        otpTokenRepository.consumeOutstanding(username, purpose, Instant.now());

        String code = generateCode(properties.getOtp().getLength());

        OtpToken token = new OtpToken();
        token.setUsername(username);
        token.setPurpose(purpose);
        token.setTargetRef(targetRef);
        token.setCodeHash(passwordEncoder.encode(code));
        token.setMaxAttempts(properties.getOtp().getMaxAttempts());
        token.setExpiresAt(Instant.now().plusSeconds(properties.getOtp().getTtlSeconds()));
        otpTokenRepository.save(token);

        if (properties.getOtp().isExposeInLogs()) {
            log.warn("DEMO MODE - OTP for {} ({}): {}", username, purpose, code);
        }
        return code;
    }

    /**
     * Verifies a submitted code.
     *
     * <p>A failed attempt increments the counter and is persisted even though
     * the method then throws - the attempt must be recorded, or the attempt
     * limit would never be reached.</p>
     *
     * @throws ApiException when the code is wrong, expired, used or exhausted
     */
    @Transactional
    public void verify(String username, Enums.OtpPurpose purpose, String targetRef, String submittedCode) {
        OtpToken token = otpTokenRepository.findLatestLive(username, purpose, targetRef)
                .orElseThrow(ApiException::invalidOtp);

        if (!token.isUsable()) {
            throw ApiException.invalidOtp();
        }

        token.setAttempts(token.getAttempts() + 1);

        if (!passwordEncoder.matches(submittedCode, token.getCodeHash())) {
            if (token.isExhausted()) {
                token.setConsumedAt(Instant.now());
                log.warn("OTP exhausted for {} purpose {}", username, purpose);
            }
            throw ApiException.invalidOtp();
        }

        token.setConsumedAt(Instant.now());
    }

    /**
     * @return true when a live challenge exists for this operation
     */
    @Transactional(readOnly = true)
    public boolean hasLiveChallenge(String username, Enums.OtpPurpose purpose, String targetRef) {
        return otpTokenRepository.findLatestLive(username, purpose, targetRef)
                .filter(OtpToken::isUsable)
                .isPresent();
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Hourly cleanup so the table does not grow without bound. */
    @Scheduled(fixedDelay = 3_600_000L)
    @Transactional
    public void purgeExpired() {
        int removed = otpTokenRepository.deleteExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        if (removed > 0) {
            log.debug("Purged {} expired OTP tokens", removed);
        }
    }
}
