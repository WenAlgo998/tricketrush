package com.ticketrush.events;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
class HoldRepository {

    private final JdbcTemplate jdbcTemplate;

    HoldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean markSeatHeldIfAvailable(UUID eventId, UUID seatId, int expectedVersion) {
        return jdbcTemplate.update("""
                UPDATE seats
                SET status = 'HELD', version = version + 1
                WHERE id = ?
                  AND event_id = ?
                  AND status = 'AVAILABLE'
                  AND version = ?
                  AND EXISTS (
                      SELECT 1
                      FROM events
                      WHERE id = ?
                        AND status = 'ON_SALE'
                        AND sale_start_at <= CURRENT_TIMESTAMP
                  )
                """, seatId, eventId, expectedVersion, eventId) == 1;
    }

    void createActiveHold(UUID holdId, UUID seatId, UUID userId, OffsetDateTime expiresAt) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO holds (id, seat_id, user_id, status, expires_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    """, holdId, seatId, userId, expiresAt);
        } catch (DuplicateKeyException exception) {
            throw new SeatHoldConflictException();
        }
    }
}
