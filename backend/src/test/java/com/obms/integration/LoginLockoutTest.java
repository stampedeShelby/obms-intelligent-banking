package com.obms.integration;

import com.obms.common.ApiException;
import com.obms.dto.AuthDtos;
import com.obms.repository.UserAccountRepository;
import com.obms.service.AuthService;
import com.obms.service.LoginAttemptService;
import com.obms.support.IntegrationTestBase;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for brute-force lockout.
 *
 * <p>A QA pass against the running application found the lockout was entirely
 * inoperative: six wrong passwords followed by the correct one signed in
 * normally. {@code AuthService.login} is {@code @Transactional}, and it
 * incremented the failure counter on the managed entity and then threw to
 * reject the attempt - so the transaction rolled back and took the increment
 * with it. {@code failedLoginAttempts} stayed at zero permanently, which made
 * online password guessing unlimited.</p>
 *
 * <p>The counter now commits in its own transaction. These tests assert the
 * observable behaviour (the attempt actually sticks) rather than the
 * mechanism, so they stay valid if the implementation changes.</p>
 */
class LoginLockoutTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private TestDataFactory factory;

    private TestDataFactory.Fixture user() {
        return factory.customerWithAccount(new BigDecimal("5000.00"));
    }

    private void attemptWrongPassword(String username) {
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest(username, "DefinitelyWrong@2026!"), "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a failed attempt survives the rejection that follows it")
    void failedAttemptIsPersisted() {
        TestDataFactory.Fixture fixture = user();

        attemptWrongPassword(fixture.username());

        assertThat(userRepository.findByUsername(fixture.username()).orElseThrow()
                .getFailedLoginAttempts())
                .as("the counter must commit even though the login threw")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the account locks once the threshold is reached")
    void accountLocksAtThreshold() {
        TestDataFactory.Fixture fixture = user();

        for (int i = 0; i < 5; i++) {
            attemptWrongPassword(fixture.username());
        }

        assertThat(userRepository.findByUsername(fixture.username()).orElseThrow().isLocked())
                .as("five failures must engage the lockout")
                .isTrue();
        assertThat(loginAttemptService.isLocked(fixture.username())).isTrue();
    }

    @Test
    @DisplayName("the correct password is refused while the account is locked")
    void correctPasswordRefusedWhileLocked() {
        TestDataFactory.Fixture fixture = user();

        for (int i = 0; i < 5; i++) {
            attemptWrongPassword(fixture.username());
        }

        // This is the exact case QA hit: the right password after the lockout.
        assertThatThrownBy(() -> authService.login(
                new AuthDtos.LoginRequest(fixture.username(), TestDataFactory.PASSWORD),
                "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("locked");
    }

    @Test
    @DisplayName("a successful sign-in clears the counter so near-misses do not accumulate")
    void successResetsTheCounter() {
        TestDataFactory.Fixture fixture = user();

        attemptWrongPassword(fixture.username());
        attemptWrongPassword(fixture.username());

        AuthDtos.LoginResponse response = authService.login(
                new AuthDtos.LoginRequest(fixture.username(), TestDataFactory.PASSWORD),
                "127.0.0.1");
        assertThat(response.otpRequired()).isTrue();

        assertThat(userRepository.findByUsername(fixture.username()).orElseThrow()
                .getFailedLoginAttempts())
                .as("a user who mistypes twice then succeeds must not creep toward a lockout")
                .isZero();
    }

    @Test
    @DisplayName("a failed attempt against an unknown username is not an error")
    void unknownUsernameIsHandledQuietly() {
        loginAttemptService.recordFailure("ghost@test.obms", "127.0.0.1");
        assertThat(loginAttemptService.isLocked("ghost@test.obms")).isFalse();
    }
}
