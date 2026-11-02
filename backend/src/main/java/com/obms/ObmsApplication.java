package com.obms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the OBMS backend.
 *
 * <p>JPA auditing is enabled here so that created/updated timestamps on every
 * entity are populated by the persistence layer rather than by service code.
 * Async and scheduling support back the notification dispatcher and the
 * monthly financial-score job.</p>
 */
/*
 * UserDetailsServiceAutoConfiguration is excluded deliberately. Without a
 * UserDetailsService bean, Spring Boot helpfully creates an in-memory "user"
 * account and prints its generated password to the log on every startup. OBMS
 * authenticates from its own users table via JWT and enables neither HTTP
 * Basic nor form login, so that account has no entry point and cannot be used
 * - but a banking application should not emit a credential to its logs, and a
 * phantom account that exists only because a bean was missing is the kind of
 * thing that becomes exploitable the day someone adds .httpBasic() for
 * convenience.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class ObmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObmsApplication.class, args);
    }
}
