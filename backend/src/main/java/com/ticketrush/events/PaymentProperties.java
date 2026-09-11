package com.ticketrush.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.payments")
public record PaymentProperties(String consumerGroup, int maxAttempts, Duration retryInterval) {

    public PaymentProperties {
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("app.payments.consumer-group must not be blank");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.payments.max-attempts must be positive");
        }
        if (retryInterval == null || retryInterval.isNegative() || retryInterval.isZero()) {
            throw new IllegalArgumentException("app.payments.retry-interval must be positive");
        }
    }
}
