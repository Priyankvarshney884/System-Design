package com.systemdesign.ecommerce.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║         Global Exception Handler — Single Place for Errors   ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Chain of Responsibility (centralized variant)
 *   All exceptions bubble up here instead of being caught per-controller.
 *   Each @ExceptionHandler is a "link in the chain" for a specific type.
 *
 * RESPONSE FORMAT: RFC 9457 Problem Details
 *   ProblemDetail is the standard Spring 6 error response format.
 *   Fields: type (URI), title, status, detail, instance.
 *   Consistent format allows API clients to handle errors generically.
 *
 * SYSTEM DESIGN: Why centralized error handling?
 *   - DRY: no duplicated try/catch in every controller
 *   - Consistent: all errors return the same structure
 *   - Observable: one place to log ALL errors with correlation IDs
 *   - Secure: prevents leaking stack traces to clients
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Domain / Application Exceptions ─────────────────────────

    /**
     * Handles all typed AppException subclasses (404, 409, 400, 401, 403).
     * The exception already carries the correct HttpStatus — map it directly.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex) {
        log.warn("Application exception: {} — {}", ex.getStatus(), ex.getMessage());
        return buildProblemDetail(ex.getStatus(), ex.getMessage());
    }

    // ── Validation Exceptions ────────────────────────────────────

    /**
     * Triggered when a @Valid / @Validated annotated request body fails.
     * Collects ALL field errors and returns them as a map.
     *
     * Example response:
     * { "errors": { "email": "must be a valid email", "password": "size must be 8-20" } }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a  // keep first error per field
                ));

        log.warn("Validation failed: {}", errors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setType(URI.create("https://api.ecommerce.com/errors/validation"));
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── Security Exceptions ──────────────────────────────────────

    /** Spring Security throws this when JWT is missing or invalid */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    /** Spring Security throws this when user lacks required role */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    // ── Catch-All ────────────────────────────────────────────────

    /**
     * Safety net — catch anything not handled above.
     * IMPORTANT: never expose internal error details to clients in production.
     * Log the full exception server-side, return a generic message to client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);  // full stack trace in logs only
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    // ── Helper ───────────────────────────────────────────────────

    private ResponseEntity<ProblemDetail> buildProblemDetail(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.ecommerce.com/errors/" + status.value()));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(problem);
    }
}
