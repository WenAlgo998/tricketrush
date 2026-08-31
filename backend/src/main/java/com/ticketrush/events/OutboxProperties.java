package com.ticketrush.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        String paymentTopic,
        Duration publishInterval,
        Duration initialDelay,
        int batchSize
) {

    public OutboxProperties {
        if (paymentTopic == null || paymentTopic.isBlank()) {
            throw new IllegalArgumentException("app.outbox.payment-topic must not be blank");
        }
        if (publishInterval == null || publishInterval.isZero() || publishInterval.isNegative()) {
            throw new IllegalArgumentException("app.outbox.publish-interval must be positive");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("app.outbox.initial-delay must not be negative");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.outbox.batch-size must be positive");
        }
    }
}
