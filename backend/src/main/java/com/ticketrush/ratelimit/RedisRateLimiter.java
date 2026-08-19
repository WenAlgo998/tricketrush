package com.ticketrush.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
class RedisRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local maximum = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= maximum then
              local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              local retryAfter = window
              if oldest[2] then
                retryAfter = math.max(1, tonumber(oldest[2]) + window - now)
              end
              return {0, retryAfter}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final Clock clock;

    RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    RateLimitDecision check(String userId) {
        if (!properties.enabled()) {
            return RateLimitDecision.permit();
        }
        try {
            List<?> result = redisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of("ratelimit:" + userId),
                    Long.toString(clock.millis()),
                    Long.toString(properties.window().toMillis()),
                    Integer.toString(properties.maxRequests()),
                    UUID.randomUUID().toString()
            );
            if (result == null || result.size() != 2) {
                throw new IllegalStateException("Unexpected rate-limit script response");
            }
            boolean allowed = ((Number) result.getFirst()).longValue() == 1;
            long retryAfterMillis = ((Number) result.get(1)).longValue();
            return allowed
                    ? RateLimitDecision.permit()
                    : RateLimitDecision.reject(Duration.ofMillis(retryAfterMillis));
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis rate limiting is unavailable; allowing authenticated request", exception);
            return RateLimitDecision.permit();
        }
    }
}
