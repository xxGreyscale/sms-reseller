package com.smsreseller.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for identity-service session / OTP / lockout state.
 *
 * <p>Redis key schema (RESEARCH lines 250-253):
 * <ul>
 *   <li>{@code refresh:{userId}:{deviceId}} — opaque refresh token (hashed), TTL 7 days (D-06, D-07, D-08)</li>
 *   <li>{@code reset:{token}} — password-reset token (single-use), TTL 1 hour (D-11)</li>
 *   <li>{@code lockout:{email}} — failed login counter, TTL = cooldown period (D-12)</li>
 *   <li>{@code nida:pending:{userId}} — NIDA verification session, TTL 10 min (CLAUDE.md NIDA guidance)</li>
 * </ul>
 *
 * <p>Connection factory is auto-configured by Spring Boot from {@code spring.data.redis.*}
 * properties (from K8s Secret in prod; from {@code application-test.yml} in tests).
 * No manual factory configuration is needed at MVP (single-node Redis).
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate for string key/value pairs.
     *
     * <p>Used for: lockout counters (INCR + EXPIRE pattern, RESEARCH atomic Redis ops),
     * reset tokens (SETEX single-use), NIDA pending session flags.
     * Connection factory is autoconfigured by Spring Boot from {@code spring.data.redis.*}.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Generic RedisTemplate with String keys and String values for structured Redis operations.
     *
     * <p>Used for: refresh token storage where we need finer control over serialization.
     * Serializers set to String to avoid Java serialization issues across deployments.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}
