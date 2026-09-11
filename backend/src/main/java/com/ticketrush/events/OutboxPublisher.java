package com.ticketrush.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            OutboxProperties properties,
            KafkaTemplate<String, String> kafkaTemplate,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.publish-interval}",
            initialDelayString = "${app.outbox.initial-delay}"
    )
    @Transactional
    public void publishReadyEvents() {
        for (OutboxEventRepository.OutboxEvent event : outboxEventRepository.lockReadyPaymentEvents(properties.batchSize())) {
            publishOrScheduleRecovery(event);
        }
    }

    private void publishOrScheduleRecovery(OutboxEventRepository.OutboxEvent event) {
        try {
            send(properties.paymentTopic(), event.aggregateId().toString(), event.payload());
            outboxEventRepository.markPublished(event.id());
            auditLogService.record("ORDER", event.aggregateId(), "PAYMENT_OUTBOX_PUBLISHED", Map.of(
                    "outboxEventId", event.id(),
                    "topic", properties.paymentTopic(),
                    "attemptCount", event.attemptCount()
            ));
        } catch (RuntimeException exception) {
            recoverDelivery(event, exception);
        }
    }

    private void recoverDelivery(OutboxEventRepository.OutboxEvent event, RuntimeException exception) {
        int failedAttemptCount = event.attemptCount() + 1;
        String error = errorMessage(exception);
        if (failedAttemptCount < properties.maxAttempts()) {
            Duration retryDelay = retryDelay(event.attemptCount());
            OffsetDateTime nextAttemptAt = OffsetDateTime.ofInstant(clock.instant().plus(retryDelay), ZoneOffset.UTC);
            outboxEventRepository.scheduleRetry(event.id(), error, nextAttemptAt);
            auditLogService.record("ORDER", event.aggregateId(), "PAYMENT_OUTBOX_RETRY_SCHEDULED", Map.of(
                    "outboxEventId", event.id(),
                    "attemptCount", failedAttemptCount,
                    "nextAttemptAt", nextAttemptAt,
                    "error", error
            ));
            return;
        }

        try {
            send(properties.paymentDlqTopic(), event.aggregateId().toString(), deadLetterPayload(event, failedAttemptCount, error));
            outboxEventRepository.markDeadLettered(event.id(), error);
            auditLogService.record("ORDER", event.aggregateId(), "PAYMENT_OUTBOX_DEAD_LETTERED", Map.of(
                    "outboxEventId", event.id(),
                    "attemptCount", failedAttemptCount,
                    "topic", properties.paymentDlqTopic(),
                    "error", error
            ));
        } catch (RuntimeException deadLetterException) {
            String deadLetterError = error + "; DLQ delivery failed: " + errorMessage(deadLetterException);
            OffsetDateTime nextAttemptAt = OffsetDateTime.ofInstant(
                    clock.instant().plus(properties.retryMaxDelay()), ZoneOffset.UTC
            );
            outboxEventRepository.scheduleRetry(event.id(), deadLetterError, nextAttemptAt);
            auditLogService.record("ORDER", event.aggregateId(), "PAYMENT_OUTBOX_RETRY_SCHEDULED", Map.of(
                    "outboxEventId", event.id(),
                    "attemptCount", failedAttemptCount,
                    "nextAttemptAt", nextAttemptAt,
                    "error", deadLetterError
            ));
        }
    }

    private void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to publish outbox event", exception.getCause());
        }
    }

    private Duration retryDelay(int previousFailureCount) {
        long multiplier = 1L << Math.min(previousFailureCount, 30);
        Duration delay = properties.retryInitialDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
    }

    private String deadLetterPayload(
            OutboxEventRepository.OutboxEvent event, int attemptCount, String error
    ) {
        try {
            return objectMapper.writeValueAsString(new DeadLetteredOutboxEvent(
                    event.id(), event.aggregateId(), event.eventType(), event.payload(), attemptCount, error
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dead-letter event", exception);
        }
    }

    private String errorMessage(Throwable exception) {
        String message = exception.getCause() != null && exception.getCause().getMessage() != null
                ? exception.getCause().getMessage()
                : exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 1_000 ? message : message.substring(0, 1_000);
    }
}
