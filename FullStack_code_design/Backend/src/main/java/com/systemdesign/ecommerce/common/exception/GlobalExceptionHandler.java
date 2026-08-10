package com.systemdesign.ecommerce.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * ║         Global Exception Handler                             ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Chain of Responsibility (centralised variant)
 *   All exceptions bubble up here — no try/catch in controllers.
 *
 * NOTE: Using explicit LoggerFactory instead of Lombok @Slf4j to ensure
 * the logger works regardless of annotation processor configuration.
 * This is infrastructure code that must never silently fail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Explicit logger — no Lombok dependency
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex) {
        log.warn("Application exception: {} — {}", ex.getStatus(), ex.getMessage());
        return buildProblemDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a
                ));

        log.warn("Validation failed: {}", errors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setType(URI.create("https://api.ecommerce.com/errors/validation"));
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    // Spring 6 / Security 6 throws AuthorizationDeniedException for .authenticated() failures
    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(
            org.springframework.security.authorization.AuthorizationDeniedException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    // Null principal when @AuthenticationPrincipal resolves to null (no token sent)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<ProblemDetail> buildProblemDetail(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.ecommerce.com/errors/" + status.value()));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(problem);
    }
}
