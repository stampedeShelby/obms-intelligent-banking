package com.obms.security;

import com.obms.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and validates the signed access tokens used for stateless auth.
 *
 * <p>Design notes worth defending in review:</p>
 * <ul>
 *   <li>Tokens are HMAC-signed (HS256) with a server-side secret. Nothing in a
 *       token is trusted without a successful signature check, so a client
 *       cannot escalate its own role by editing the payload.</li>
 *   <li>Roles are embedded as a claim to avoid a database read per request,
 *       but every money-moving endpoint still re-checks ownership against the
 *       database. The token answers "who are you", never "what do you own".</li>
 *   <li>A short lifetime (30 minutes by default) limits the window in which a
 *       stolen token is useful, since this design has no revocation list.</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_CUSTOMER_ID = "cid";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    /** Marks a token that has passed password check but not yet OTP. */
    public static final String TYPE_PRE_AUTH = "pre-auth";
    /** Marks a fully authenticated token. */
    public static final String TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final int accessTokenMinutes;

    public JwtService(AppProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "obms.security.jwt-secret must be at least 32 bytes. Set OBMS_JWT_SECRET.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = properties.getSecurity().getAccessTokenMinutes();
    }

    /**
     * Issues a full access token.
     *
     * @param username   subject of the token
     * @param roles      role names without the {@code ROLE_} prefix
     * @param customerId owning customer, null for staff users with no customer record
     * @return signed compact JWT
     */
    public String issueAccessToken(String username, List<String> roles, Long customerId) {
        return build(username, roles, customerId, TYPE_ACCESS, accessTokenMinutes);
    }

    /**
     * Issues a short-lived token that proves the password step succeeded but
     * carries no authority. It is accepted only by the OTP verification
     * endpoint, which is what stops a caller from skipping the second factor.
     */
    public String issuePreAuthToken(String username) {
        return build(username, List.of(), null, TYPE_PRE_AUTH, 5);
    }

    private String build(String username, List<String> roles, Long customerId,
                         String tokenType, int minutes) {
        Instant now = Instant.now();
        Instant expiry = now.plus(minutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_CUSTOMER_ID, customerId)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies the signature and expiry of a token.
     *
     * @return the claims when the token is valid, otherwise empty
     */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // Expected for expired or tampered tokens; log without the token value.
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    public Long customerIdOf(Claims claims) {
        Object raw = claims.get(CLAIM_CUSTOMER_ID);
        return raw instanceof Number number ? number.longValue() : null;
    }

    public String tokenTypeOf(Claims claims) {
        Object raw = claims.get(CLAIM_TOKEN_TYPE);
        return raw instanceof String type ? type : TYPE_ACCESS;
    }

    public int accessTokenSeconds() {
        return accessTokenMinutes * 60;
    }
}
