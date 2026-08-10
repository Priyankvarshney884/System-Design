package com.systemdesign.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Redis Configuration                                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: Redis use-cases in this application
 *
 *  1. Cache-Aside (most common pattern)
 *     App checks Redis first → if miss → load from DB → store in Redis.
 *     Used for: product catalog, user profiles, category trees.
 *
 *  2. Session Store
 *     JWT refresh tokens stored in Redis with TTL = token expiry.
 *     Allows instant token revocation (logout from all devices).
 *
 *  3. Rate Limiting
 *     Token bucket per IP/userId stored as Redis counter with expiry.
 *     Atomic increment via Lua script = no race conditions.
 *
 *  4. Distributed Locks
 *     SETNX (SET if Not eXists) pattern for inventory reservation
 *     prevents overselling when 1000 users buy the last item simultaneously.
 *
 * PERFORMANCE NOTE:
 *   Lettuce (async Netty-based) > Jedis (sync, blocking) for high throughput.
 *   Spring Boot auto-configures Lettuce when spring-boot-starter-data-redis
 *   is on the classpath.
 */
@Configuration
public class RedisConfig {

    // TTL constants — centralized so they're easy to tune
    public static final String CACHE_PRODUCTS     = "products";
    public static final String CACHE_CATEGORIES   = "categories";
    public static final String CACHE_USER_PROFILE = "user-profiles";

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Lettuce connection factory.
     * In production: swap localhost with Redis Cluster endpoint
     * or use LettuceClusterConfiguration for Redis Cluster mode.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * Generic RedisTemplate<String, Object>
     * Used for direct Redis operations (rate limiting counters, locks).
     *
     * Serialization:
     *  - Keys:   StringRedisSerializer  → human-readable keys in Redis CLI
     *  - Values: GenericJackson2JsonRedisSerializer → JSON (not Java binary)
     *
     * WHY JSON over Java serialization?
     *   Binary Java serialization breaks when classes change (versions).
     *   JSON is language-agnostic — any service can read the cache.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache abstraction backed by Redis.
     * Configured with PER-CACHE TTLs — different data has different staleness tolerance.
     *
     * SYSTEM DESIGN: Cache TTL strategy
     *   - Products: 10 min — changes infrequently, slightly stale is acceptable
     *   - Categories: 1 hour — very rarely changes
     *   - User profiles: 5 min — balance freshness vs DB load
     *
     *  Cache invalidation is the hard part:
     *   On update → @CacheEvict("products") removes stale entry.
     *   On create → no eviction needed (new key).
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // Default: JSON values, disable caching of null values
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(CACHE_PRODUCTS,
                defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put(CACHE_CATEGORIES,
                defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put(CACHE_USER_PROFILE,
                defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
