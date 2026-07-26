package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class SeatMapRepository {

    private static final RowMapper<SeatMapItem> SEAT_ROW_MAPPER = (resultSet, rowNumber) -> new SeatMapItem(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("section"),
            resultSet.getString("row"),
            resultSet.getString("seat_number"),
            resultSet.getInt("price_cents"),
            resultSet.getString("currency").trim(),
            SeatStatus.valueOf(resultSet.getString("status")),
            resultSet.getInt("version")
    );

    private final JdbcTemplate jdbcTemplate;

    SeatMapRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<SeatMapItem> findByEventId(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT id, section, row, seat_number, price_cents, currency, status, version
                FROM seats
                WHERE event_id = ?
                ORDER BY section ASC, row ASC, seat_number ASC
                """, SEAT_ROW_MAPPER, eventId);
    }
}
