package com.systemdesign.ecommerce.module.auth.controller;

import com.systemdesign.ecommerce.common.response.ApiResponse;
import com.systemdesign.ecommerce.module.auth.dto.AuthDto;
import com.systemdesign.ecommerce.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Auth Controller — REST Endpoints                   ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Controller (thin layer)
 *   Controller ONLY does:
 *   1. Extract request data
 *   2. Call service method
 *   3. Wrap result in ApiResponse
 *
 *   NO business logic here — it all lives in AuthService.
 *   This makes controllers easy to test and understand.
 *
 * REST API Design:
 *   POST /auth/register  → 201 Created
 *   POST /auth/login     → 200 OK
 *   POST /auth/logout    → 200 OK
 *   POST /auth/refresh   → 200 OK
 *   GET  /auth/me        → 200 OK
 *
 * Versioning: /api/v1/ prefix — allows breaking changes in v2 without
 * breaking existing clients.
 *
 * @AuthenticationPrincipal: injects userId from SecurityContext
 * (set by JwtAuthenticationFilter) — no manual token parsing in controller.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register, login, logout, token refresh")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── POST /api/v1/auth/register ────────────────────────────────

    /**
     * Register a new user account.
     * Returns 201 Created with tokens + user profile on success.
     *
     * @Valid triggers Bean Validation — errors → 400 via GlobalExceptionHandler
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register new user", description = "Creates account and returns JWT tokens")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest request) {

        AuthDto.AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", response));
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────

    /**
     * Authenticate with email + password.
     * Returns 200 OK with tokens + user profile on success.
     * Returns 401 on invalid credentials (same message for security).
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {

        AuthDto.AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────

    /**
     * Invalidate current access token.
     * Requires: Authorization: Bearer <access_token>
     *
     * @AuthenticationPrincipal = userId extracted from JWT by JwtAuthenticationFilter
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout", description = "Invalidate current token (blacklists it in Redis)")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {

        // Strip "Bearer " prefix before passing to service
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────

    /**
     * Exchange refresh token for new access token.
     * Called by Angular interceptor silently when access token expires (401).
     *
     * SYSTEM DESIGN: Silent refresh flow
     *   No user interaction needed — Angular handles this automatically.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthDto.TokenRefreshResponse>> refresh(
            @Valid @RequestBody AuthDto.RefreshTokenRequest request) {

        AuthDto.TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /api/v1/auth/me ───────────────────────────────────────

    /**
     * Get current authenticated user's profile.
     * Called by Angular on app load to restore user session from token.
     * Requires valid JWT in Authorization header.
     *
     * @AuthenticationPrincipal automatically resolves to userId string
     * (the principal set in JwtAuthenticationFilter → SecurityContext)
     */
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current user", description = "Returns authenticated user profile")
    public ResponseEntity<ApiResponse<AuthDto.UserDto>> getMe(
            @AuthenticationPrincipal String userId) {

        // Spring Security sets principal to "anonymousUser" string when unauthenticated
        // We check for null AND the anonymous string
        if (userId == null || userId.equals("anonymousUser")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        AuthDto.UserDto user = authService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
