package com.systemdesign.ecommerce.module.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Token Blacklist Service — Redis                    ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: JWT Revocation Problem
 *
 *   JWT tokens are stateless — once issued, they're valid until expiry.
 *   Problem: user logs out but their token is still valid for 15 minutes.
 *   An attacker who stole the token can still use it.
 *
 * SOLUTION: Token Blacklist in Redis
 *   On logout → store the token's jti (unique ID) in Redis with TTL = remaining expiry.
 *   On every authenticated request → check Redis blacklist BEFORE trusting the token.
 *   When token naturally expires → Redis entry also expires → auto-cleanup.
 *
 * WHY REDIS and not PostgreSQL for blacklist?
 *   - Redis TTL handles cleanup automatically (no cron job needed)
 *   - Redis is ~100x faster than PostgreSQL for key lookups
 *   - Blacklist check happens on EVERY API request → must be sub-millisecond
 *   - PostgreSQL can't afford a DB round-trip per request at scale
 *
 * INTERVIEW ANSWER: "We store invalidated JWT IDs in Redis with TTL equal to the
 * token's remaining lifetime. This gives us stateful logout while keeping the
 * stateless benefits of JWT for the 99.9% of requests that are not blacklisted."
 *
 * Key format: "token:blacklist:{jti}"
 * Value: "revoked" (small string)
 * TTL: remaining milliseconds until token expires
 */
@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenBlacklistService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Add a token to the blacklist.
     * Called on: logout, password change, security event.
     *
     * @param jti      unique token ID (from JWT's jti claim)
     * @param ttlMs    remaining milliseconds until token naturally expires
     */
    public void blacklist(String jti, long ttlMs) {
        if (ttlMs <= 0) return;  // already expired — no point blacklisting

        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(ttlMs));
        log.debug("Token blacklisted: jti={}, ttl={}ms", jti, ttlMs);
    }

    /**
     * Check if a token is blacklisted.
     * Called on every authenticated API request — must be fast.
     * Redis O(1) GET operation → sub-millisecond response.
     *
     * @param jti unique token ID
     * @return true if the token has been revoked
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
        );
    }

    /**
     * Store refresh token in Redis.
     * Key: "token:refresh:{userId}"
     * Value: the refresh token string
     * TTL: 7 days
     *
     * SYSTEM DESIGN: Only one refresh token per user.
     * New login overwrites old one → previous devices are automatically logged out.
     * For multi-device support, use "token:refresh:{userId}:{deviceId}" instead.
     */
    public void storeRefreshToken(String userId, String refreshToken, long ttlMs) {
        String key = "token:refresh:" + userId;
        redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(ttlMs));
    }

    /**
     * Retrieve stored refresh token for a user.
     * Used to validate that the refresh token matches what was issued.
     */
    public String getRefreshToken(String userId) {
        Object value = redisTemplate.opsForValue().get("token:refresh:" + userId);
        return value != null ? value.toString() : null;
    }

    /** Delete refresh token on logout — prevents any further token refresh */
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete("token:refresh:" + userId);
    }
}
