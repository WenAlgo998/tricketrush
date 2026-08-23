package com.ticketrush.events.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CheckoutIntegrationTest {

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
    private UUID firstSeatId;
    private UUID secondSeatId;
    private String buyerToken;

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
        firstSeatId = UUID.randomUUID();
        secondSeatId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
                VALUES (?, ?, ?, ?, ?, 'ON_SALE')
                """, eventId, "Checkout Test Event", "Test Arena", now.minusHours(1), now.plusDays(1));
        insertSeat(firstSeatId, "1");
        insertSeat(secondSeatId, "2");
        buyerToken = registerAndGetToken("buyer@example.com");
    }

    @Test
    void createsOnePendingOrderAndConsumesTheBuyerActiveHolds() throws Exception {
        UUID firstHoldId = createHold(firstSeatId, buyerToken, 0);
        UUID secondHoldId = createHold(secondSeatId, buyerToken, 0);
        UUID idempotencyKey = UUID.randomUUID();

        String response = checkout(buyerToken, idempotencyKey, firstHoldId, secondHoldId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(response).get("orderId").asText());

        checkout(buyerToken, idempotencyKey, firstHoldId, secondHoldId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_seats WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM holds WHERE status = 'CONSUMED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void rejectsHoldsThatAreNotOwnedOrNoLongerActive() throws Exception {
        UUID holdId = createHold(firstSeatId, buyerToken, 0);
        String otherBuyerToken = registerAndGetToken("other@example.com");

        checkout(otherBuyerToken, UUID.randomUUID(), holdId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_HOLD_CONFLICT"));

        jdbcTemplate.update("UPDATE holds SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", holdId);
        checkout(buyerToken, UUID.randomUUID(), holdId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKOUT_HOLD_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    void validatesTheIdempotencyKeyAndHoldList() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions checkout(
            String token, UUID idempotencyKey, UUID... holdIds
    ) throws Exception {
        String body = "{\"holdIds\":[%s]}".formatted(java.util.Arrays.stream(holdIds)
                .map(id -> "\"%s\"".formatted(id))
                .collect(java.util.stream.Collectors.joining(",")));
        return mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private UUID createHold(UUID seatId, String token, int expectedVersion) throws Exception {
        String response = mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":%d}".formatted(expectedVersion)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("holdId").asText());
    }

    private String registerAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-horse"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void insertSeat(UUID seatId, String seatNumber) {
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency)
                VALUES (?, ?, 'A', '1', ?, 7500, 'USD')
                """, seatId, eventId, seatNumber);
    }
}
