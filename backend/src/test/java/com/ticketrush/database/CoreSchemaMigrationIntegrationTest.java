package com.ticketrush.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude="
})
class CoreSchemaMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheCoreSchema() {
        Integer coreTableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'users', 'events', 'seats', 'holds',
                      'orders', 'order_seats', 'payments', 'outbox_events'
                  )
                """, Integer.class);

        Boolean migrationSucceeded = jdbcTemplate.queryForObject("""
                SELECT success
                FROM flyway_schema_history
                WHERE version = '1'
                """, Boolean.class);

        Integer activeHoldIndexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uq_holds_active_seat'
                """, Integer.class);

        assertThat(coreTableCount).isEqualTo(8);
        assertThat(migrationSucceeded).isTrue();
        assertThat(activeHoldIndexCount).isEqualTo(1);
    }
}
