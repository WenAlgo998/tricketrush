package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class ReservationRepository {

    private final JdbcTemplate jdbcTemplate;

    ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean markSeatSoldIfAvailable(UUID eventId, UUID seatId) {
        return jdbcTemplate.update("""
                UPDATE seats
                SET status = 'SOLD', version = version + 1
                WHERE id = ?
                  AND event_id = ?
                  AND status = 'AVAILABLE'
                  AND EXISTS (
                      SELECT 1
                      FROM events
                      WHERE id = ?
                        AND status = 'ON_SALE'
                        AND sale_start_at <= CURRENT_TIMESTAMP
                  )
                """, seatId, eventId, eventId) == 1;
    }

    void createConfirmedOrder(UUID orderId, UUID userId, UUID eventId, UUID seatId) {
        jdbcTemplate.update("""
                INSERT INTO orders (id, user_id, event_id, status, idempotency_key)
                VALUES (?, ?, ?, 'CONFIRMED', ?)
                """, orderId, userId, eventId, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO order_seats (order_id, seat_id)
                VALUES (?, ?)
                """, orderId, seatId);
    }
}
