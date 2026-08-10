package com.systemdesign.ecommerce.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Auth DTOs — Request & Response Objects             ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: DTO (Data Transfer Object)
 *   Never expose the JPA Entity directly in API responses.
 *   Reasons:
 *   1. Security: Entity has passwordHash — never send it to client
 *   2. Versioning: API contract is separate from DB schema
 *
 * Java 21 Records: immutable, compact, no Lombok needed.
 *   NOTE: Records CANNOT use Lombok @Builder — records already
 *   have a canonical constructor. We use the constructor directly
 *   or a static factory method instead.
 */
public class AuthDto {

    // ── Request DTOs ─────────────────────────────────────────────

    public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
    ) {}

    public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
    ) {}

    public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────

    /**
     * Returned on successful login or register.
     * Static factory method used instead of @Builder (records don't support it).
     */
    public record AuthResponse(
        String  accessToken,
        String  refreshToken,
        String  tokenType,
        long    expiresIn,
        UserDto user
    ) {
        // Static factory — clean call site: AuthResponse.of(token, refresh, user)
        public static AuthResponse of(String accessToken, String refreshToken,
                                      long expiresIn, UserDto user) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
        }
    }

    public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long   expiresIn
    ) {
        public static TokenRefreshResponse of(String token, long expiresIn) {
            return new TokenRefreshResponse(token, "Bearer", expiresIn);
        }
    }

    /**
     * User profile — safe to send to client (no passwordHash).
     */
    public record UserDto(
        String  id,
        String  name,
        String  email,
        Set<String> roles,
        String  avatarUrl,
        boolean active
    ) {}
}
