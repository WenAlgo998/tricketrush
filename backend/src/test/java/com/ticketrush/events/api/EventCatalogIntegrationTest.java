package com.ticketrush.events.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude="
})
@AutoConfigureMockMvc
class EventCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID earlierEventId;

    @BeforeEach
    void seedEvents() {
        jdbcTemplate.update("DELETE FROM events");

        earlierEventId = UUID.randomUUID();
        insertEvent(
                earlierEventId,
                "Spring Lights Festival",
                "Harbor Arena",
                OffsetDateTime.of(2026, 4, 1, 14, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 5, 10, 19, 30, 0, 0, ZoneOffset.UTC),
                "SCHEDULED"
        );
        insertEvent(
                UUID.randomUUID(),
                "Summer Sound",
                "Riverside Pavilion",
                OffsetDateTime.of(2026, 5, 1, 14, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 6, 20, 20, 0, 0, 0, ZoneOffset.UTC),
                "ON_SALE"
        );
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void listsEventsWithDeterministicPagination() throws Exception {
        mockMvc.perform(get("/api/events").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(earlierEventId.toString()))
                .andExpect(jsonPath("$.content[0].name").value("Spring Lights Festival"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void returnsEventDetails() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}", earlierEventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(earlierEventId.toString()))
                .andExpect(jsonPath("$.venueName").value("Harbor Arena"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void returnsStructuredNotFoundErrorForUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found"))
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/events").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void insertEvent(
            UUID id,
            String name,
            String venueName,
            OffsetDateTime saleStartAt,
            OffsetDateTime eventStartAt,
            String status
    ) {
        jdbcTemplate.update("""
                INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, name, venueName, saleStartAt, eventStartAt, status);
    }
}
