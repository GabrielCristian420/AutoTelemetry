package com.gabrielbicu.telemetry.exception;

/**
 * Thrown when a service operation is rejected because it would violate a
 * business rule on an existing entity (e.g. ending a trip that is already
 * ended). Currently bubbles up as a 500; in Week 4 the
 * {@code GlobalExceptionHandler} will map it to HTTP 409 Conflict with a
 * small JSON body.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
