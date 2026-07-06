package com.gabrielbicu.telemetry.exception;

import com.gabrielbicu.telemetry.service.AuthService.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates service-layer exceptions into consistent HTTP responses with an
 * {@link ApiError} body.
 *
 * <p>Routing table (worth knowing cold for an interview):
 * <ul>
 *   <li>{@link EntityNotFoundException}     → 404
 *   <li>{@link BusinessRuleException}       → 409 (duplicate email, trip already ended)
 *   <li>{@link InvalidCredentialsException} → 401 (wrong email/password — same response shape
 *       for "no such user" and "wrong password" to avoid account enumeration)
 *   <li>{@link MethodArgumentNotValidException} → 400 with per-field messages,
 *       so the client can show what needs fixing without guessing
 *   <li>{@link AccessDeniedException}       → 403 (authenticated but not allowed; we don't
 *       really use roles yet, but the handler is ready for when ADMIN-only routes arrive)
 *   <li>{@link Exception} catch-all         → 500, with a generic message that doesn't leak
 *       the stack trace to the client. The real stack still goes to the server logs.
 * </ul>
 *
 * <p>Why centralized instead of per-controller? One place to evolve error
 * semantics, no risk of forgetting an exception in some controller. For a CV
 * project, having one @RestControllerAdvice also signals that you understand
 * this is the idiomatic Spring pattern.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(404, ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(401, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(403, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Validation failed", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        // Don't leak internals to the client; the stack is still in the server logs.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, "Unexpected error"));
    }
}
