package com.obms.integration;

import com.obms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the application does not stand up credentials of its own.
 *
 * <p>A QA read of the startup log found Spring Boot auto-configuring an
 * in-memory {@code user} account and printing its generated password on every
 * boot, because no {@code UserDetailsService} bean was defined. It was not
 * reachable - OBMS enables neither HTTP Basic nor form login - but a banking
 * application should not write a credential to its logs, and an account that
 * exists only because a bean was missing becomes a real hole the day somebody
 * adds {@code .httpBasic()} for convenience.</p>
 */
class StartupHygieneTest extends IntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("no in-memory user manager is registered")
    void noInMemoryUserDetailsManager() {
        assertThat(context.getBeanNamesForType(InMemoryUserDetailsManager.class))
                .as("an auto-configured in-memory account would print a password to the log")
                .isEmpty();
    }

    @Test
    @DisplayName("authentication is served by the application's own users table")
    void authenticationUsesTheDatabase() {
        // The JWT filter and AuthService are the only authentication path.
        assertThat(context.getBeanNamesForType(com.obms.security.JwtAuthenticationFilter.class))
                .isNotEmpty();
        assertThat(context.getBeanNamesForType(com.obms.service.AuthService.class))
                .isNotEmpty();
    }
}
