package com.ticketrush.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.outbox")
public record OutboxProperties(
        String paymentTopic,
        String paymentDlqTopic,
        Duration publishInterval,
        Duration initialDelay,
        int batchSize,
        int maxAttempts,
        Duration retryInitialDelay,
        Duration retryMaxDelay
) {

    public OutboxProperties {
        if (paymentTopic == null || paymentTopic.isBlank()) {
            throw new IllegalArgumentException("app.outbox.payment-topic must not be blank");
        }
        if (paymentDlqTopic == null || paymentDlqTopic.isBlank()) {
            throw new IllegalArgumentException("app.outbox.payment-dlq-topic must not be blank");
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
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.outbox.max-attempts must be positive");
        }
        if (retryInitialDelay == null || retryInitialDelay.isZero() || retryInitialDelay.isNegative()) {
            throw new IllegalArgumentException("app.outbox.retry-initial-delay must be positive");
        }
        if (retryMaxDelay == null || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("app.outbox.retry-max-delay must not be shorter than retry-initial-delay");
        }
    }
}
