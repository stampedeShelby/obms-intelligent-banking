package com.obms.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * The authenticated caller, as the rest of the application sees them.
 *
 * <p>Carrying {@code customerId} on the principal means service code can scope
 * every query to the caller's own data without re-reading the user record, and
 * means an endpoint that forgets to scope is visible in review as a missing
 * use of this field rather than as a subtle data leak.</p>
 *
 * @param username   login name
 * @param customerId owning customer, null for staff accounts
 * @param roles      role names without the {@code ROLE_} prefix
 * @param tokenType  {@code access} or {@code pre-auth}
 */
public record ObmsPrincipal(String username, Long customerId, List<String> roles, String tokenType)
        implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /** Not exposed; this principal is built from a verified token, not a password. */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * @return the caller's customer id, failing loudly if this is a staff
     *         principal with no customer record rather than returning null
     *         into a query that would then match nothing silently
     */
    public Long requireCustomerId() {
        if (customerId == null) {
            throw new IllegalStateException("This operation requires a customer principal.");
        }
        return customerId;
    }
}
