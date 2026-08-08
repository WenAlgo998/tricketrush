package com.ticketrush.events.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.events.HoldExpiryService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude="
})
@AutoConfigureMockMvc
class HoldLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HoldExpiryService holdExpiryService;

    private UUID eventId;
    private UUID seatId;
    private UserSession owner;
    private UserSession otherBuyer;

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
                """, eventId, "Hold Lifecycle Event", "Test Arena", now.minusHours(1), now.plusDays(1));
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency)
                VALUES (?, ?, 'A', '1', '1', 7500, 'USD')
                """, seatId, eventId);
        owner = registerUser("owner@example.com");
        otherBuyer = registerUser("other@example.com");
    }

    @Test
    void ownerCanReleaseAnActiveHoldAndOtherBuyersCannot() throws Exception {
        UUID holdId = createHold(owner.accessToken());

        mockMvc.perform(delete("/api/holds/{holdId}", holdId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(delete("/api/holds/{holdId}", holdId)
                        .header("Authorization", "Bearer " + otherBuyer.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_OWNED"));

        mockMvc.perform(delete("/api/holds/{holdId}", holdId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, holdId))
                .isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject("SELECT released_at IS NOT NULL FROM holds WHERE id = ?", Boolean.class, holdId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId))
                .isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM seats WHERE id = ?", Integer.class, seatId))
                .isEqualTo(2);

        mockMvc.perform(delete("/api/holds/{holdId}", holdId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM seats WHERE id = ?", Integer.class, seatId))
                .isEqualTo(2);
    }

    @Test
    void expirySweepReleasesOnlyTheSeatOwnedByAnExpiredActiveHold() {
        UUID expiredSeatId = UUID.randomUUID();
        UUID expiredHoldId = UUID.randomUUID();
        UUID futureSeatId = UUID.randomUUID();
        UUID futureHoldId = UUID.randomUUID();
        UUID consumedSeatId = UUID.randomUUID();
        UUID consumedHoldId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        insertSeat(expiredSeatId, "2", "HELD", 4);
        insertHold(expiredHoldId, expiredSeatId, owner.id(), "ACTIVE", now.minusMinutes(1));
        insertSeat(futureSeatId, "3", "HELD", 8);
        insertHold(futureHoldId, futureSeatId, owner.id(), "ACTIVE", now.plusMinutes(1));
        insertSeat(consumedSeatId, "4", "SOLD", 12);
        insertHold(consumedHoldId, consumedSeatId, owner.id(), "CONSUMED", now.minusMinutes(1));

        holdExpiryService.expireDueHolds();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, expiredHoldId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, expiredSeatId))
                .isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM seats WHERE id = ?", Integer.class, expiredSeatId))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, futureHoldId))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, futureSeatId))
                .isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, consumedHoldId))
                .isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, consumedSeatId))
                .isEqualTo("SOLD");

        holdExpiryService.expireDueHolds();
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM seats WHERE id = ?", Integer.class, expiredSeatId))
                .isEqualTo(5);
    }

    private UUID createHold(String token) throws Exception {
        String response = mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("holdId").asText());
    }

    private UserSession registerUser(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return new UserSession(UUID.fromString(body.get("userId").asText()), body.get("accessToken").asText());
    }

    private void insertSeat(UUID id, String seatNumber, String status, int version) {
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency, status, version)
                VALUES (?, ?, 'A', '1', ?, 7500, 'USD', ?, ?)
                """, id, eventId, seatNumber, status, version);
    }

    private void insertHold(UUID id, UUID holdSeatId, UUID userId, String status, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO holds (id, seat_id, user_id, status, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, holdSeatId, userId, status, expiresAt);
    }

    private record UserSession(UUID id, String accessToken) {
    }
}
