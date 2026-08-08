package com.ticketrush.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.holds.expiry")
public record HoldExpiryProperties(Duration sweepInterval, Duration initialDelay, int batchSize) {

    public HoldExpiryProperties {
        if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
            throw new IllegalArgumentException("app.holds.expiry.sweep-interval must be positive");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("app.holds.expiry.initial-delay must not be negative");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.holds.expiry.batch-size must be positive");
        }
    }
}
