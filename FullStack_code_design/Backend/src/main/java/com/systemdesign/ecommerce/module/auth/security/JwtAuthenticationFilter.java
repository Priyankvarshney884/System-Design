package com.systemdesign.ecommerce.module.auth.security;

import com.systemdesign.ecommerce.module.auth.repository.UserRepository;
import com.systemdesign.ecommerce.module.auth.service.JwtService;
import com.systemdesign.ecommerce.module.auth.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║         JWT Authentication Filter                            ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Chain of Responsibility
 *   This filter sits in Spring Security's filter chain.
 *   Every HTTP request passes through it BEFORE reaching the controller.
 *
 * Execution order per request:
 *   1. Extract "Authorization: Bearer <token>" header
 *   2. Validate JWT signature + expiry
 *   3. Check Redis blacklist (logout/revocation check)
 *   4. Load user from DB (or cache)
 *   5. Set Authentication in SecurityContext
 *   6. Call filterChain.doFilter() → next filter → controller
 *
 * SYSTEM DESIGN: Why OncePerRequestFilter?
 *   Guarantees exactly-once execution per request, even if the request
 *   is forwarded internally (e.g., error dispatch).
 *
 * PERFORMANCE: This filter runs on EVERY authenticated request.
 *   - JWT validation: in-memory crypto → ~1ms
 *   - Redis blacklist check: ~0.5ms
 *   - DB user load: ~2ms (or Redis cache hit: ~0.5ms)
 *   Total overhead per request: ~3-4ms — acceptable at scale.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService            jwtService;
    private final TokenBlacklistService blacklistService;
    private final UserRepository        userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenBlacklistService blacklistService,
                                   UserRepository userRepository) {
        this.jwtService       = jwtService;
        this.blacklistService = blacklistService;
        this.userRepository   = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No token → skip this filter → Spring Security handles as anonymous
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);  // strip "Bearer "

        try {
            // ── Step 1: Validate JWT signature + expiry ──────────────
            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // ── Step 2: Check Redis blacklist ────────────────────────
            // SYSTEM DESIGN: this is the logout/revocation check.
            // If user logged out, their token jti is in Redis → reject.
            String jti = jwtService.extractJti(token);
            if (blacklistService.isBlacklisted(jti)) {
                log.debug("Rejected blacklisted token jti={}", jti);
                filterChain.doFilter(request, response);
                return;
            }

            // ── Step 3: Set SecurityContext (if not already set) ─────
            String userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<String> roles = jwtService.extractRoles(token);

                // Build authorities from token claims — no DB call for roles
                // SYSTEM DESIGN: roles in JWT = no DB round-trip per request
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                // Verify user still exists and is active
                // NOTE: For ultra-high performance, cache this in Redis too
                boolean userActive = userRepository
                        .findByIdWithRoles(UUID.fromString(userId))
                        .map(u -> u.isActive() && !u.isDeleted())
                        .orElse(false);

                if (userActive) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userId,       // principal = userId string
                            null,         // credentials = null (token IS the credential)
                            authorities
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (Exception e) {
            // Log and continue — Spring Security will handle as unauthenticated
            log.debug("JWT filter error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skip JWT filter entirely for public endpoints.
     * Avoids unnecessary token parsing for /auth/login, /auth/register, Swagger.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/register")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/actuator/health");
    }
}
