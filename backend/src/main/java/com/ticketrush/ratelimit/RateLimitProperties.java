package com.ticketrush.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Duration window, int maxRequests) {

    public RateLimitProperties {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("app.rate-limit.window must be positive");
        }
        if (maxRequests < 1) {
            throw new IllegalArgumentException("app.rate-limit.max-requests must be positive");
        }
    }
}
