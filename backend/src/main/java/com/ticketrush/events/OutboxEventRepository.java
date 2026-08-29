package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
