package com.obms.support;

import com.obms.domain.Role;
import com.obms.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.util.List;

/**
 * Mints access tokens for API tests.
 *
 * <p>Tests cannot complete the real two-factor flow, because the one-time code
 * is hashed on write and never returned by any endpoint - which is itself a
 * property the suite asserts elsewhere. So API tests obtain a token by signing
 * one with the same {@link JwtService} the application uses.</p>
 *
 * <p>This is not a bypass of the security model under test: the filter chain,
 * the authority checks and every ownership check still run exactly as they do
 * in production. Only the OTP step is skipped, and the OTP mechanism has its
 * own dedicated tests.</p>
 */
@TestComponent
@RequiredArgsConstructor
public class ApiTestTokens {

    private final JwtService jwtService;

    /**
     * @param fixture the customer to authenticate as
     * @return a bearer token carrying that customer's real roles and id
     */
    public String accessTokenFor(TestDataFactory.Fixture fixture) {
        List<String> roles = fixture.user().getRoles().stream()
                .map(Role::getName)
                .sorted()
                .toList();
        return jwtService.issueAccessToken(fixture.username(), roles,
                fixture.customer().getId());
    }

    /**
     * @return a token with the given roles, for testing authority boundaries
     */
    public String tokenWithRoles(TestDataFactory.Fixture fixture, String... roles) {
        return jwtService.issueAccessToken(fixture.username(), List.of(roles),
                fixture.customer().getId());
    }

    /**
     * @return a syntactically valid but wrongly signed token
     */
    public String forgedToken() {
        return "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlckB0ZXN0Lm9ibXMiLCJyb2xlcyI6"
                + "WyJBRE1JTiJdLCJjaWQiOjEsInR5cCI6ImFjY2VzcyIsImV4cCI6NTAwMDAwMDAwMH0."
                + "ZmFrZXNpZ25hdHVyZXRoYXR3aWxsbm90dmVyaWZ5";
    }
}
