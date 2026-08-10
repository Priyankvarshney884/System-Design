package com.systemdesign.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║             Global Application Exception Hierarchy           ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Exception Hierarchy
 *   One root exception (AppException) carries an HttpStatus.
 *   Specific sub-exceptions are self-documenting and caught
 *   centrally by GlobalExceptionHandler.
 *
 * NOTE: No Lombok here — this is a plain class so it works even
 * if annotation processing is not fully configured in the IDE.
 * Exceptions are framework infrastructure, not domain logic.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // ── Specific exceptions ─────────────────────────────────────

    /** 404 — Resource not found */
    public static class ResourceNotFoundException extends AppException {
        public ResourceNotFoundException(String resource, Object id) {
            super(resource + " not found with id: " + id, HttpStatus.NOT_FOUND);
        }
    }

    /** 409 — Conflict (e.g. duplicate email) */
    public static class ConflictException extends AppException {
        public ConflictException(String message) {
            super(message, HttpStatus.CONFLICT);
        }
    }

    /** 400 — Bad request */
    public static class BadRequestException extends AppException {
        public BadRequestException(String message) {
            super(message, HttpStatus.BAD_REQUEST);
        }
    }

    /** 401 — Unauthenticated */
    public static class UnauthorizedException extends AppException {
        public UnauthorizedException(String message) {
            super(message, HttpStatus.UNAUTHORIZED);
        }
    }

    /** 403 — Authenticated but not allowed */
    public static class ForbiddenException extends AppException {
        public ForbiddenException(String message) {
            super(message, HttpStatus.FORBIDDEN);
        }
    }
}
