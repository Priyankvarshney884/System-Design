package com.systemdesign.ecommerce.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║             Global Application Exception Hierarchy           ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Exception Hierarchy
 *   One root exception (AppException) carries an HttpStatus.
 *   Specific sub-exceptions are self-documenting and caught
 *   centrally by GlobalExceptionHandler — no try/catch scattered
 *   across controllers.
 *
 * INTERVIEW TALKING POINT:
 *   "We use a typed exception hierarchy so the GlobalExceptionHandler
 *   can map domain errors to the correct HTTP status codes without
 *   every controller needing its own error-handling logic."
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    // ── Specific exceptions ─────────────────────────────────────

    /** 404 — Resource not found in DB or cache */
    public static class ResourceNotFoundException extends AppException {
        public ResourceNotFoundException(String resource, Object id) {
            super(resource + " not found with id: " + id, HttpStatus.NOT_FOUND);
        }
    }

    /** 409 — Conflict (e.g. duplicate email, out-of-stock) */
    public static class ConflictException extends AppException {
        public ConflictException(String message) {
            super(message, HttpStatus.CONFLICT);
        }
    }

    /** 400 — Bad request (validation failed, malformed input) */
    public static class BadRequestException extends AppException {
        public BadRequestException(String message) {
            super(message, HttpStatus.BAD_REQUEST);
        }
    }

    /** 401 — Unauthenticated (invalid or expired JWT) */
    public static class UnauthorizedException extends AppException {
        public UnauthorizedException(String message) {
            super(message, HttpStatus.UNAUTHORIZED);
        }
    }

    /** 403 — Authenticated but not allowed (wrong role) */
    public static class ForbiddenException extends AppException {
        public ForbiddenException(String message) {
            super(message, HttpStatus.FORBIDDEN);
        }
    }
}
