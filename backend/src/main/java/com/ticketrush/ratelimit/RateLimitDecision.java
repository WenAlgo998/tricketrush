package com.ticketrush.ratelimit;

import java.time.Duration;

public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    static RateLimitDecision permit() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    static RateLimitDecision reject(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
