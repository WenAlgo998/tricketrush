package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class EventCatalogRepository {

    private static final RowMapper<EventCatalogItem> EVENT_ROW_MAPPER = (resultSet, rowNumber) -> new EventCatalogItem(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("name"),
            resultSet.getString("venue_name"),
            resultSet.getObject("sale_start_at", OffsetDateTime.class),
            resultSet.getObject("event_start_at", OffsetDateTime.class),
            EventStatus.valueOf(resultSet.getString("status"))
    );

    private final JdbcTemplate jdbcTemplate;

    EventCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    EventCatalogPage findPage(int limit, int offset) {
        List<EventCatalogItem> events = jdbcTemplate.query("""
                SELECT id, name, venue_name, sale_start_at, event_start_at, status
                FROM events
                ORDER BY sale_start_at ASC, id ASC
                LIMIT ? OFFSET ?
                """, EVENT_ROW_MAPPER, limit, offset);

        Long totalElements = jdbcTemplate.queryForObject("SELECT count(*) FROM events", Long.class);
        return new EventCatalogPage(events, totalElements == null ? 0 : totalElements);
    }

    Optional<EventCatalogItem> findById(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT id, name, venue_name, sale_start_at, event_start_at, status
                FROM events
                WHERE id = ?
                """, EVENT_ROW_MAPPER, eventId).stream().findFirst();
    }
}
