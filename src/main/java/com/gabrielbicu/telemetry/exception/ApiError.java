package com.gabrielbicu.telemetry.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by the API whenever a request fails.
 *
 * <p>The shape stays the same regardless of the underlying error type — only
 * {@code status} and {@code message} differ — so clients have one parser to
 * write. The {@code errors} list carries field-level validation detail and is
 * empty for non-validation failures.
 *
 * <p>{@code timestamp} is server time in UTC; resolved at the moment the
 * exception is caught so the client doesn't need to trust its own clock for
 * ordering errors from the server.
 */
public record ApiError(int status, String message, Instant timestamp, List<String> errors) {

    public ApiError(int status, String message) {
        this(status, message, Instant.now(), List.of());
    }

    public ApiError(int status, String message, List<String> errors) {
        this(status, message, Instant.now(), errors);
    }
}
