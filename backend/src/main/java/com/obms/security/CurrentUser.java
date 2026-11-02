package com.obms.security;

import com.obms.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the authenticated principal out of the security context.
 *
 * <p>Service code calls {@link #requireCustomerId()} rather than accepting a
 * customer id as a request parameter. That is the single most important
 * anti-IDOR measure in the application: because the identity comes from the
 * verified token and never from the request body, a caller cannot ask for
 * somebody else's data by changing a number in the payload.</p>
 */
@Component
public class CurrentUser {

    public Optional<ObmsPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ObmsPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public ObmsPrincipal require() {
        return principal().orElseThrow(() -> ApiException.unauthorized("Not authenticated."));
    }

    public String username() {
        return require().username();
    }

    /**
     * @return the customer id of the caller
     * @throws ApiException when the caller is a staff principal with no customer record
     */
    public Long requireCustomerId() {
        ObmsPrincipal principal = require();
        if (principal.customerId() == null) {
            throw ApiException.forbidden("This endpoint is for customer accounts only.");
        }
        return principal.customerId();
    }

    /** Username for audit entries when no principal is present. */
    public String actorOrSystem() {
        return principal().map(ObmsPrincipal::username).orElse("SYSTEM");
    }
}
