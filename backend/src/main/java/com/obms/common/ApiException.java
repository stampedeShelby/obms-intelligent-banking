package com.obms.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Application-level exception carrying an HTTP status and a stable error code.
 *
 * <p>Every failure the API reports deliberately goes through this type so that
 * the response shape is uniform and so that no internal exception message ever
 * reaches a client verbatim. The static factories below name the failure modes
 * the banking domain actually has, which keeps call sites readable.</p>
 */
@Getter
public class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    // ---- 400 -------------------------------------------------------------

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException insufficientFunds() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS",
                "The account does not have enough available balance for this transfer.");
    }

    public static ApiException limitExceeded(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_EXCEEDED", message);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", message);
    }

    // ---- 401 / 403 -------------------------------------------------------

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid username or password.");
    }

    public static ApiException invalidOtp() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_OTP",
                "The code is incorrect or has expired.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /**
     * Raised when an authenticated user reaches for a resource owned by
     * somebody else. Deliberately distinct from {@link #notFound} at the call
     * site but mapped to the same client-visible wording by the handler, so
     * the API does not confirm the existence of other customers' records.
     */
    public static ApiException notOwned(String resource) {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_OWNED",
                "You do not have access to this " + resource + ".");
    }

    // ---- 404 / 409 -------------------------------------------------------

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " was not found.");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException duplicateRequest() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REQUEST",
                "This request has already been processed.");
    }

    // ---- 503 -------------------------------------------------------------

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}
