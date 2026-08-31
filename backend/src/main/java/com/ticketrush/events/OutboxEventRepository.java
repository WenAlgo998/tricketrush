package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void enqueuePaymentRequested(UUID orderId, UUID userId, UUID eventId) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events (id, aggregate_id, event_type, payload)
                VALUES (?, ?, 'PaymentRequested', ?::jsonb)
                """, UUID.randomUUID(), orderId, """
                {"orderId":"%s","userId":"%s","eventId":"%s"}
                """.formatted(orderId, userId, eventId));
    }

    List<OutboxEvent> lockReadyPaymentEvents(int batchSize) {
        return jdbcTemplate.query("""
                        SELECT id, aggregate_id, payload::text
                        FROM outbox_events
                        WHERE published = FALSE
                          AND event_type = 'PaymentRequested'
                          AND next_attempt_at <= CURRENT_TIMESTAMP
                        ORDER BY created_at ASC, id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """, (resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("aggregate_id", UUID.class),
                        resultSet.getString("payload")
                ), batchSize);
    }

    void markPublished(UUID outboxEventId) {
        jdbcTemplate.update("""
                UPDATE outbox_events
                SET published = TRUE, published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND published = FALSE
                """, outboxEventId);
    }

    record OutboxEvent(UUID id, UUID aggregateId, String payload) {
    }
}
