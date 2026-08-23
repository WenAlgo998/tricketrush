package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CheckoutRepository {

    private final JdbcTemplate jdbcTemplate;

    CheckoutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<OrderSummary> findOrderByIdempotencyKey(UUID userId, UUID idempotencyKey) {
        return jdbcTemplate.query("""
                        SELECT id, status
                        FROM orders
                        WHERE user_id = ? AND idempotency_key = ?
                        """, (resultSet, rowNumber) -> new OrderSummary(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("status")
                ), userId, idempotencyKey)
                .stream()
                .findFirst();
    }

    List<HoldEvent> findHoldEvents(UUID userId, List<UUID> holdIds) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(holdIds.size(), "?"));
        List<Object> parameters = new ArrayList<>(holdIds);
        parameters.add(userId);
        return jdbcTemplate.query("""
                        SELECT h.id, s.event_id
                        FROM holds h
                        JOIN seats s ON s.id = h.seat_id
                        WHERE h.id IN (%s)
                          AND h.user_id = ?
                        """.formatted(placeholders), (resultSet, rowNumber) -> new HoldEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("event_id", UUID.class)
                ), parameters.toArray());
    }

    boolean createPendingOrder(UUID orderId, UUID userId, UUID eventId, UUID idempotencyKey) {
        return !jdbcTemplate.query("""
                        INSERT INTO orders (id, user_id, event_id, status, idempotency_key)
                        VALUES (?, ?, ?, 'PENDING', ?)
                        ON CONFLICT (user_id, idempotency_key) DO NOTHING
                        RETURNING id
                        """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                orderId, userId, eventId, idempotencyKey).isEmpty();
    }

    Optional<ConsumedHold> consumeActiveHold(UUID holdId, UUID userId) {
        return jdbcTemplate.query("""
                        UPDATE holds
                        SET status = 'CONSUMED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND user_id = ?
                          AND status = 'ACTIVE'
                          AND expires_at > CURRENT_TIMESTAMP
                        RETURNING seat_id
                        """, (resultSet, rowNumber) -> new ConsumedHold(
                        resultSet.getObject("seat_id", UUID.class)
                ), holdId, userId)
                .stream()
                .findFirst();
    }

    void createOrderSeat(UUID orderId, UUID seatId) {
        jdbcTemplate.update("""
                INSERT INTO order_seats (order_id, seat_id)
                VALUES (?, ?)
                """, orderId, seatId);
    }

    record HoldEvent(UUID holdId, UUID eventId) {
    }

    record ConsumedHold(UUID seatId) {
    }

    record OrderSummary(UUID orderId, String status) {
    }
}
