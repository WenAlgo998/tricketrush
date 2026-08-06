package com.ticketrush.events.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude="
})
@AutoConfigureMockMvc
class HoldIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 200;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID eventId;
    private UUID seatId;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM order_seats");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM holds");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM seats");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM users");

        eventId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
                VALUES (?, ?, ?, ?, ?, 'ON_SALE')
                """, eventId, "Hold Test Event", "Test Arena", now.minusHours(1), now.plusDays(1));
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency)
                VALUES (?, ?, 'A', '1', '1', 7500, 'USD')
                """, seatId, eventId);
        accessToken = registerAndGetToken();
    }

    @Test
    void createsADurableFiveMinuteHoldAndTransitionsTheSeatWithOptimisticLocking() throws Exception {
        OffsetDateTime beforeRequest = OffsetDateTime.now(ZoneOffset.UTC);
        String response = mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID holdId = UUID.fromString(objectMapper.readTree(response).get("holdId").asText());
        OffsetDateTime expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM holds WHERE id = ?", OffsetDateTime.class, holdId);
        assertThat(expiresAt).isAfter(beforeRequest.plusMinutes(4)).isBefore(beforeRequest.plusMinutes(6));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, holdId))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM seats WHERE id = ?", Integer.class, seatId))
                .isEqualTo(1);
    }

    @Test
    void permitsExactlyOneOfTwoHundredConcurrentFirstHoldAttempts() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int attempt = 0; attempt < CONCURRENT_ATTEMPTS; attempt++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(15, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                                    .header("Authorization", "Bearer " + accessToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"expectedVersion\":0}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> response : responses) {
                statuses.add(response.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(199);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM holds WHERE status = 'ACTIVE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId))
                .isEqualTo("HELD");
    }

    @Test
    void rejectsMissingOrStaleVersionsWithStructuredErrors() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_HOLD_CONFLICT"));
    }

    private String registerAndGetToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"buyer@example.com","password":"correct-horse"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
