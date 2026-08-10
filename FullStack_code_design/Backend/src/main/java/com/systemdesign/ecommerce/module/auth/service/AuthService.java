package com.systemdesign.ecommerce.module.auth.service;

import com.systemdesign.ecommerce.common.exception.AppException;
import com.systemdesign.ecommerce.module.auth.dto.AuthDto;
import com.systemdesign.ecommerce.module.auth.entity.User;
import com.systemdesign.ecommerce.module.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Auth Service — Core Business Logic                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Facade
 *   AuthService is a facade over:
 *   - UserRepository (DB operations)
 *   - JwtService (token creation/validation)
 *   - TokenBlacklistService (Redis token store)
 *   - PasswordEncoder (BCrypt)
 *
 *   Controller calls ONE method → service orchestrates everything.
 *
 * DESIGN PATTERN: Strategy (PasswordEncoder)
 *   PasswordEncoder is injected — BCryptPasswordEncoder by default.
 *   To switch to Argon2 → change the bean, no service code changes.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository        userRepository;
    private final JwtService            jwtService;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder       passwordEncoder;

    // Explicit constructor — no Lombok @RequiredArgsConstructor needed
    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       TokenBlacklistService blacklistService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.jwtService      = jwtService;
        this.blacklistService = blacklistService;
        this.passwordEncoder  = passwordEncoder;
    }

    // ── Register ──────────────────────────────────────────────────

    /**
     * Register a new user account.
     *
     * Steps:
     *  1. Check email uniqueness (fail fast with clear error)
     *  2. Hash password with BCrypt (cost=12)
     *  3. Persist User entity
     *  4. Generate access + refresh tokens
     *  5. Store refresh token in Redis
     *  6. Return AuthResponse
     *
     * @throws AppException.ConflictException if email already registered
     */
    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new AppException.ConflictException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(Set.of(User.Role.USER))
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: id={}, email={}", saved.getId(), saved.getEmail());

        return buildAuthResponse(saved);
    }

    // ── Login ──────────────────────────────────────────────────────

    /**
     * Authenticate user with email + password.
     *
     * SECURITY: Same error message for not-found and wrong-password.
     * Different messages leak user existence → account enumeration attack.
     *
     * @throws AppException.UnauthorizedException on invalid credentials
     */
    @Transactional(readOnly = true)
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findActiveByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new AppException.UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException.UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return buildAuthResponse(user);
    }

    // ── Logout ──────────────────────────────────────────────────────

    /**
     * Invalidate both tokens on logout.
     * 1. Blacklist the access token jti in Redis (TTL = remaining expiry)
     * 2. Delete the refresh token from Redis
     */
    public void logout(String accessToken) {
        try {
            String jti    = jwtService.extractJti(accessToken);
            String userId = jwtService.extractUserId(accessToken);
            long   ttlMs  = jwtService.getRemainingTtlMs(accessToken);

            blacklistService.blacklist(jti, ttlMs);
            blacklistService.deleteRefreshToken(userId);

            log.info("User logged out: userId={}", userId);
        } catch (Exception e) {
            log.warn("Logout failed for token: {}", e.getMessage());
            // Don't throw — logout should always succeed from the client's perspective
        }
    }

    // ── Token Refresh ──────────────────────────────────────────────

    /**
     * Issue a new access token using a valid refresh token.
     *
     * SYSTEM DESIGN: Silent refresh flow
     *   Angular interceptor catches 401 → calls /auth/refresh automatically.
     *   User never sees a login prompt during normal usage.
     *
     * @throws AppException.UnauthorizedException if refresh token is invalid/expired/revoked
     */
    public AuthDto.TokenRefreshResponse refresh(AuthDto.RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtService.isTokenValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new AppException.UnauthorizedException("Invalid refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);

        // Verify this is the exact token we issued (prevents replay)
        String storedToken = blacklistService.getRefreshToken(userId);
        if (!refreshToken.equals(storedToken)) {
            throw new AppException.UnauthorizedException("Refresh token has been revoked");
        }

        // Reload user to get current roles (may have changed since token was issued)
        User user = userRepository.findByIdWithRoles(UUID.fromString(userId))
                .orElseThrow(() -> new AppException.UnauthorizedException("User not found"));

        Set<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        String newAccessToken = jwtService.generateAccessToken(userId, roles);
        long   expiresIn      = jwtService.getAccessTokenExpiryMs() / 1000;

        return AuthDto.TokenRefreshResponse.of(newAccessToken, expiresIn);
    }

    // ── Get Current User ───────────────────────────────────────────

    /**
     * Return current authenticated user's profile.
     * Called by /auth/me — Angular uses this on app load to restore session.
     */
    @Transactional(readOnly = true)
    public AuthDto.UserDto getMe(String userId) {
        User user = userRepository.findByIdWithRoles(UUID.fromString(userId))
                .orElseThrow(() -> new AppException.ResourceNotFoundException("User", userId));
        return toUserDto(user);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private AuthDto.AuthResponse buildAuthResponse(User user) {
        String      userId       = user.getId().toString();
        Set<String> roles        = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
        String      accessToken  = jwtService.generateAccessToken(userId, roles);
        String      refreshToken = jwtService.generateRefreshToken(userId);
        long        expiresIn    = jwtService.getAccessTokenExpiryMs() / 1000;

        blacklistService.storeRefreshToken(userId, refreshToken, jwtService.getRefreshTokenExpiryMs());

        return AuthDto.AuthResponse.of(accessToken, refreshToken, expiresIn, toUserDto(user));
    }

    private AuthDto.UserDto toUserDto(User user) {
        return new AuthDto.UserDto(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                user.getAvatarUrl(),
                user.isActive()
        );
    }
}
