package com.ticketrush.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.holds")
public record HoldProperties(Duration duration) {

    public HoldProperties {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("app.holds.duration must be positive");
        }
    }
}
