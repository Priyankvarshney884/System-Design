package com.systemdesign.ecommerce.module.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           JWT Service — Token Creation &amp; Validation      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: JWT (JSON Web Token) — stateless authentication
 *
 *   Traditional sessions: server stores session in DB/Redis → stateful.
 *   JWT: all user info encoded IN the token → stateless.
 *
 *   Token structure: header.payload.signature
 *     header:    { "alg": "HS256", "typ": "JWT" }
 *     payload:   { "sub": "userId", "roles": ["USER"], "exp": 1234567890 }
 *     signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
 *
 *   Verification: recalculate signature → if it matches → token is valid.
 *   NO database lookup needed for every request → scales to millions of RPS.
 *
 * ACCESS TOKEN vs REFRESH TOKEN:
 *   Access Token:  short-lived (15 min) — used for API calls
 *   Refresh Token: long-lived (7 days)  — used ONLY to get a new access token
 *
 *   Why two tokens?
 *   - Short access token = small attack window if stolen (expires fast)
 *   - Long refresh token = user doesn't re-login every 15 min
 *   - Refresh token stored in Redis → can be instantly revoked (logout)
 *
 * DESIGN PATTERN: Service (encapsulates JWT library, hides JJWT API)
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;     // 15 min

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;   // 7 days

    // ── Token Creation ────────────────────────────────────────────

    /**
     * Generate an ACCESS token.
     * Payload contains: userId, roles, issued-at, expiry.
     *
     * @param userId  UUID of the authenticated user (subject claim)
     * @param roles   user's roles — encoded as a list claim
     * @return signed JWT string
     */
    public String generateAccessToken(String userId, Collection<String> roles) {
        return buildToken(userId, roles, accessTokenExpiryMs, "access");
    }

    /**
     * Generate a REFRESH token.
     * Simpler payload — only userId + expiry.
     * Does NOT contain roles (refresh endpoint doesn't need them).
     *
     * @param userId UUID of the authenticated user
     * @return signed JWT string
     */
    public String generateRefreshToken(String userId) {
        return buildToken(userId, List.of(), refreshTokenExpiryMs, "refresh");
    }

    /**
     * Core token builder.
     * jti (JWT ID) = random UUID — makes every token unique.
     * Prevents replay attacks: even two tokens issued at the same millisecond differ.
     */
    private String buildToken(String subject, Collection<String> roles,
                              long expiryMs, String tokenType) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())   // jti — unique token ID
                .claim("type", tokenType)
                .signWith(getSigningKey());

        if (!roles.isEmpty()) {
            builder.claim("roles", roles);
        }

        return builder.compact();
    }

    // ── Token Validation ──────────────────────────────────────────

    /**
     * Validate token signature + expiry.
     * Returns true only if:
     *   1. Signature is valid (not tampered)
     *   2. Token has not expired
     *
     * SYSTEM DESIGN: validation is stateless — no Redis lookup here.
     * Revocation check (blacklist) happens BEFORE this in the filter.
     */
    public boolean isTokenValid(String token) {
        try {
            getClaims(token);  // throws if invalid or expired
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is specifically a refresh token (not an access token).
     * Prevents using an access token on the /refresh endpoint.
     */
    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(getClaim(token, c -> c.get("type", String.class)));
        } catch (JwtException e) {
            return false;
        }
    }

    // ── Claims Extraction ─────────────────────────────────────────

    /** Extract userId (subject claim) from token */
    public String extractUserId(String token) {
        return getClaim(token, Claims::getSubject);
    }

    /** Extract expiry date — used to calculate remaining TTL for Redis */
    public Date extractExpiry(String token) {
        return getClaim(token, Claims::getExpiration);
    }

    /** Unique token ID — stored in Redis blacklist on logout */
    public String extractJti(String token) {
        return getClaim(token, Claims::getId);
    }

    /** Extract remaining TTL in milliseconds — used to set Redis expiry */
    public long getRemainingTtlMs(String token) {
        Date expiry = extractExpiry(token);
        long remaining = expiry.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return getClaim(token, c -> c.get("roles", List.class));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private <T> T getClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(getClaims(token));
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Build HMAC-SHA256 signing key from the secret string.
     * Key must be ≥ 256 bits for HS256.
     * SYSTEM DESIGN: in production use RS256 (asymmetric) so
     * resource servers can verify tokens without sharing the secret.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getAccessTokenExpiryMs()  { return accessTokenExpiryMs; }
    public long getRefreshTokenExpiryMs() { return refreshTokenExpiryMs; }
}
