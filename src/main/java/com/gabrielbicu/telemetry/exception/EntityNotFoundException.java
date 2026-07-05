package com.gabrielbicu.telemetry.exception;

/**
 * Thrown when a service lookup cannot find an entity (or in our case, cannot
 * find one that belongs to the calling user — the two cases deliberately
 * collapse into the same 404 to avoid leaking which ids exist).
 *
 * <p>This is a plainRuntimeException subclass for now. In Week 4 it will be
 * picked up by the {@code GlobalExceptionHandler} (@RestControllerAdvice) and
 * mapped to an HTTP 404 response with a small JSON body. Until then it bubbles
 * up as a 500 — that's expected, the proper mapping lands with auth.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityName, Long id) {
        super(entityName + " with id " + id + " was not found");
    }
}
