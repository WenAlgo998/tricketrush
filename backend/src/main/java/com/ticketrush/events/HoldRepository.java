package com.ticketrush.events;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    Optional<ActiveHold> findActiveUnexpiredById(UUID holdId) {
        return jdbcTemplate.query("""
                        SELECT id, seat_id, user_id
                        FROM holds
                        WHERE id = ?
                          AND status = 'ACTIVE'
                          AND expires_at > CURRENT_TIMESTAMP
                        """, (resultSet, rowNum) -> new ActiveHold(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("seat_id", UUID.class),
                        resultSet.getObject("user_id", UUID.class)
                ), holdId)
                .stream()
                .findFirst();
    }

    boolean releaseIfActiveAndOwned(UUID holdId, UUID userId) {
        return jdbcTemplate.update("""
                UPDATE holds
                SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND user_id = ?
                  AND status = 'ACTIVE'
                  AND expires_at > CURRENT_TIMESTAMP
                """, holdId, userId) == 1;
    }

    List<UUID> findDueActiveHoldIds(int batchSize) {
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM holds
                WHERE status = 'ACTIVE'
                  AND expires_at <= CURRENT_TIMESTAMP
                ORDER BY expires_at ASC
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, UUID.class, batchSize);
    }

    boolean expireIfDue(UUID holdId) {
        return jdbcTemplate.update("""
                UPDATE holds
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                  AND expires_at <= CURRENT_TIMESTAMP
                """, holdId) == 1;
    }

    void releaseSeatForInactiveHold(UUID holdId, String holdStatus) {
        jdbcTemplate.update("""
                UPDATE seats
                SET status = 'AVAILABLE', version = version + 1
                WHERE status = 'HELD'
                  AND id = (
                      SELECT seat_id
                      FROM holds
                      WHERE id = ?
                        AND status = ?
                  )
                """, holdId, holdStatus);
    }

    record ActiveHold(UUID id, UUID seatId, UUID userId) {
    }
}
