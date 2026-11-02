package com.obms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strongly typed binding for the {@code obms.*} configuration block.
 *
 * <p>Using typed properties rather than scattered {@code @Value} annotations
 * keeps every tunable in one reviewable place, which matters here because
 * several of them are security parameters (lockout thresholds, OTP lifetime,
 * risk cut-offs).</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "obms")
public class AppProperties {

    private Security security = new Security();
    private Otp otp = new Otp();
    private Bank bank = new Bank();
    private Transfer transfer = new Transfer();
    private Fraud fraud = new Fraud();
    private Copilot copilot = new Copilot();

    @Getter
    @Setter
    public static class Security {
        /** HMAC signing key for JWTs. Overridden by environment in deployment. */
        private String jwtSecret;
        private int accessTokenMinutes = 30;
        private int maxFailedLogins = 5;
        private int lockoutMinutes = 15;
    }

    @Getter
    @Setter
    public static class Otp {
        private int length = 6;
        private int ttlSeconds = 300;
        private int maxAttempts = 5;
        /** Demo aid only; prints the OTP to the log. Never enable in deployment. */
        private boolean exposeInLogs;
    }

    @Getter
    @Setter
    public static class Bank {
        private String name = "OBMS Bank";
        private String ifsc = "OBMS0000001";
        private String branch = "Bengaluru Main";
    }

    @Getter
    @Setter
    public static class Transfer {
        private BigDecimal defaultDailyLimit = new BigDecimal("100000.00");
        private BigDecimal alwaysVerifyAbove = new BigDecimal("25000.00");
    }

    @Getter
    @Setter
    public static class Fraud {
        private String serviceUrl = "http://localhost:8000";
        private boolean enabled = true;
        private int timeoutMs = 3000;
        private int mediumThreshold = 40;
        private int highThreshold = 65;
        private int criticalThreshold = 85;
    }

    @Getter
    @Setter
    public static class Copilot {
        private String serviceUrl = "http://localhost:8000";
        private boolean enabled = true;
        private int timeoutMs = 15000;
    }
}
