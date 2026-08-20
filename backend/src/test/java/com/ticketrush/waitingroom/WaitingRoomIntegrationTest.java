package com.ticketrush.waitingroom;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude=",
        "app.rate-limit.enabled=false",
        "app.waiting-room.max-active-admissions=1",
        "app.waiting-room.admission-token-ttl=PT0.2S",
        "app.waiting-room.estimated-admission-interval=PT30S"
})
@AutoConfigureMockMvc
class WaitingRoomIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.10-alpine3.21")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID eventId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_seats");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM holds");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM seats");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM users");

        eventId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO events (id, name, venue_name, sale_start_at, event_start_at, status)
                VALUES (?, ?, ?, ?, ?, 'ON_SALE')
                """, eventId, "Queue Test Event", "Test Arena", now.minusHours(1), now.plusDays(1));
    }

    @Test
    void maintainsFifoOrderAndReleasesTheNextBuyerWhenAnAdmissionExpires() throws Exception {
        String firstBuyerToken = registerAndGetToken("first@example.com");
        String secondBuyerToken = registerAndGetToken("second@example.com");

        mockMvc.perform(post("/api/events/{eventId}/queue/join", eventId)
                        .header("Authorization", "Bearer " + firstBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.estimatedWaitSeconds").value(0));

        mockMvc.perform(post("/api/events/{eventId}/queue/join", eventId)
                        .header("Authorization", "Bearer " + secondBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.estimatedWaitSeconds").value(30));

        String firstStatus = mockMvc.perform(get("/api/events/{eventId}/queue/status", eventId)
                        .header("Authorization", "Bearer " + firstBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admitted").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String admissionToken = objectMapper.readTree(firstStatus).get("token").asText();

        mockMvc.perform(get("/api/events/{eventId}/queue/status", eventId)
                        .header("Authorization", "Bearer " + secondBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admitted").value(false))
                .andExpect(jsonPath("$.token").value(nullValue()));

        mockMvc.perform(get("/api/events/{eventId}/queue/status", eventId)
                        .header("Authorization", "Bearer " + firstBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admitted").value(true))
                .andExpect(jsonPath("$.token").value(admissionToken));

        Thread.sleep(300);

        mockMvc.perform(get("/api/events/{eventId}/queue/status", eventId)
                        .header("Authorization", "Bearer " + secondBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admitted").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void makesQueueJoinsIdempotentAndRequiresExistingEvents() throws Exception {
        String buyerToken = registerAndGetToken("buyer@example.com");

        mockMvc.perform(post("/api/events/{eventId}/queue/join", eventId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));
        mockMvc.perform(post("/api/events/{eventId}/queue/join", eventId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(post("/api/events/{eventId}/queue/join", UUID.randomUUID())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    private String registerAndGetToken(String email) throws Exception {
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
        String token = body.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
