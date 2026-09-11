package com.ticketrush.events;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;
import java.util.UUID;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
class PaymentConsumerRecoveryConfiguration {

    @Bean
    CommonErrorHandler paymentConsumerErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties outboxProperties,
            PaymentProperties paymentProperties,
            AuditLogService auditLogService
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> {
            UUID aggregateId = aggregateId(record.key() == null ? null : record.key().toString());
            auditLogService.record("ORDER", aggregateId, "PAYMENT_EVENT_DEAD_LETTERED", Map.of(
                    "sourceTopic", record.topic(),
                    "sourcePartition", record.partition(),
                    "sourceOffset", record.offset(),
                    "error", errorMessage(exception)
            ));
            return new TopicPartition(outboxProperties.paymentDlqTopic(), record.partition());
        });
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(paymentProperties.retryInterval().toMillis(), paymentProperties.maxAttempts() - 1L)
        );
    }

    private UUID aggregateId(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return new UUID(0, 0);
        }
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 1_000 ? message : message.substring(0, 1_000);
    }
}
