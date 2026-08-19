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
        "spring.autoconfigure.exclude=",
        "app.rate-limit.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class SeatMapIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID eventId;
    private UUID availableSeatId;

    @BeforeEach
    void seedSeats() {
        jdbcTemplate.update("DELETE FROM seats");
        jdbcTemplate.update("DELETE FROM events");

        eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                eventId,
                "Spring Lights Festival",
                "Harbor Arena",
                OffsetDateTime.of(2026, 4, 1, 14, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 5, 10, 19, 30, 0, 0, ZoneOffset.UTC),
                "ON_SALE"
        );

        availableSeatId = UUID.randomUUID();
        insertSeat(availableSeatId, "A", "1", "1", 7_500, "USD", "AVAILABLE", 0);
        insertSeat(UUID.randomUUID(), "A", "1", "2", 7_500, "USD", "HELD", 3);
        insertSeat(UUID.randomUUID(), "B", "2", "1", 5_000, "USD", "SOLD", 1);
    }

    @Test
    void returnsOrderedSeatMapWithReservationFields() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}/seats", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(availableSeatId.toString()))
                .andExpect(jsonPath("$[0].section").value("A"))
                .andExpect(jsonPath("$[0].row").value("1"))
                .andExpect(jsonPath("$[0].seatNumber").value("1"))
                .andExpect(jsonPath("$[0].priceCents").value(7_500))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[1].seatNumber").value("2"))
                .andExpect(jsonPath("$[2].section").value("B"));
    }

    @Test
    void returnsAnEmptySeatMapForEventWithoutSeats() throws Exception {
        jdbcTemplate.update("DELETE FROM seats WHERE event_id = ?", eventId);

        mockMvc.perform(get("/api/events/{eventId}/seats", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsNotFoundForUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}/seats", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    private void insertSeat(
            UUID id,
            String section,
            String row,
            String seatNumber,
            int priceCents,
            String currency,
            String status,
            int version
    ) {
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency, status, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, eventId, section, row, seatNumber, priceCents, currency, status, version);
    }
}
