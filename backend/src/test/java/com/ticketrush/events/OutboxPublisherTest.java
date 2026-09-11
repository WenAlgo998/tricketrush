package com.ticketrush.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AuditLogService auditLogService;

    @Captor
    private ArgumentCaptor<String> payloadCaptor;

    @Test
    void schedulesAnExponentialRetryAfterAPublishFailure() {
        OutboxEventRepository.OutboxEvent event = event(0);
        when(outboxEventRepository.lockReadyPaymentEvents(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("payment-events"), eq(event.aggregateId().toString()), eq(event.payload())))
                .thenReturn(failedSend("broker unavailable"));

        publisher().publishReadyEvents();

        verify(outboxEventRepository).scheduleRetry(
                eq(event.id()), eq("broker unavailable"),
                eq(OffsetDateTime.ofInstant(CLOCK.instant().plusSeconds(1), ZoneOffset.UTC))
        );
        verify(auditLogService).record(eq("ORDER"), eq(event.aggregateId()),
                eq("PAYMENT_OUTBOX_RETRY_SCHEDULED"), any());
    }

    @Test
    void routesAnExhaustedPublishFailureToThePaymentDlq() throws Exception {
        OutboxEventRepository.OutboxEvent event = event(2);
        when(outboxEventRepository.lockReadyPaymentEvents(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("payment-events"), eq(event.aggregateId().toString()), eq(event.payload())))
                .thenReturn(failedSend("broker unavailable"));
        when(kafkaTemplate.send(eq("payment-events-dlq"), eq(event.aggregateId().toString()), payloadCaptor.capture()))
                .thenReturn(successfulSend());

        publisher().publishReadyEvents();

        JsonNode deadLetter = new ObjectMapper().readTree(payloadCaptor.getValue());
        assertThat(deadLetter.get("outboxEventId").asText()).isEqualTo(event.id().toString());
        assertThat(deadLetter.get("aggregateId").asText()).isEqualTo(event.aggregateId().toString());
        assertThat(deadLetter.get("attemptCount").asInt()).isEqualTo(3);
        assertThat(deadLetter.get("error").asText()).isEqualTo("broker unavailable");
        verify(outboxEventRepository).markDeadLettered(event.id(), "broker unavailable");
        verify(auditLogService).record(eq("ORDER"), eq(event.aggregateId()),
                eq("PAYMENT_OUTBOX_DEAD_LETTERED"), any());
    }

    private OutboxPublisher publisher() {
        return new OutboxPublisher(
                outboxEventRepository,
                new OutboxProperties(
                        "payment-events", "payment-events-dlq", Duration.ofSeconds(1), Duration.ZERO,
                        100, 3, Duration.ofSeconds(1), Duration.ofMinutes(1)
                ),
                kafkaTemplate,
                auditLogService,
                new ObjectMapper(),
                CLOCK
        );
    }

    private OutboxEventRepository.OutboxEvent event(int previousFailureCount) {
        UUID orderId = UUID.randomUUID();
        return new OutboxEventRepository.OutboxEvent(
                UUID.randomUUID(), orderId, "PaymentRequested",
                "{\"orderId\":\"%s\"}".formatted(orderId), previousFailureCount
        );
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<SendResult<String, String>> failedSend(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }
}
