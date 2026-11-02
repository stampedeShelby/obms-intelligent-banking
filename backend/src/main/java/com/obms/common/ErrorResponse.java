package com.obms.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body returned by every failing endpoint.
 *
 * @param timestamp     when the failure was rendered
 * @param status        HTTP status code
 * @param code          stable machine-readable error code
 * @param message       safe, user-facing description
 * @param path          request path that failed
 * @param fieldErrors   per-field validation messages, absent when not applicable
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ErrorResponse validation(String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), 400, "VALIDATION_FAILED",
                "One or more fields are invalid.", path, fieldErrors);
    }
}
