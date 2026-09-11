package com.ticketrush.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void append(String aggregateType, UUID aggregateId, String action, String details) {
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, aggregate_type, aggregate_id, action, details)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), aggregateType, aggregateId, action, details);
    }
}
