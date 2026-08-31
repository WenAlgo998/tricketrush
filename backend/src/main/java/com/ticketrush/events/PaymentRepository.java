package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<PendingPaymentOrder> findPendingOrderForUpdate(UUID orderId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, event_id
                        FROM orders
                        WHERE id = ?
                          AND status = 'PENDING'
                        FOR UPDATE
                        """, (resultSet, rowNumber) -> new PendingPaymentOrder(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getObject("event_id", UUID.class)
                ), orderId)
                .stream()
                .findFirst();
    }

    void createPayment(UUID orderId, PaymentResult paymentResult) {
        jdbcTemplate.update("""
                INSERT INTO payments (id, order_id, status, provider_ref)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (order_id) DO NOTHING
                """, UUID.randomUUID(), orderId, paymentResult.status(), paymentResult.providerReference());
    }

    void markOrderConfirmed(UUID orderId) {
        jdbcTemplate.update("""
                UPDATE orders
                SET status = 'CONFIRMED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                """, orderId);
    }

    void markOrderFailed(UUID orderId) {
        jdbcTemplate.update("""
                UPDATE orders
                SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                """, orderId);
    }

    List<SeatTransition> markOrderSeatsSold(UUID orderId) {
        return updateOrderSeats(orderId, "SOLD");
    }

    List<SeatTransition> releaseOrderSeats(UUID orderId) {
        return updateOrderSeats(orderId, "AVAILABLE");
    }

    private List<SeatTransition> updateOrderSeats(UUID orderId, String status) {
        return jdbcTemplate.query("""
                        UPDATE seats
                        SET status = ?, version = version + 1
                        WHERE status = 'HELD'
                          AND id IN (
                              SELECT seat_id
                              FROM order_seats
                              WHERE order_id = ?
                          )
                        RETURNING event_id, id, version
                        """, (resultSet, rowNumber) -> new SeatTransition(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getObject("id", UUID.class),
                        resultSet.getInt("version")
                ), status, orderId);
    }

    record PendingPaymentOrder(UUID orderId, UUID userId, UUID eventId) {
    }

    record SeatTransition(UUID eventId, UUID seatId, int version) {
    }
}
