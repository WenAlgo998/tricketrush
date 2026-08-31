package com.ticketrush.events.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.events.OutboxPublisher;
import com.ticketrush.events.PaymentProvider;
import com.ticketrush.events.PaymentResult;
import com.ticketrush.events.PaymentWorker;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.autoconfigure.exclude=",
        "app.kafka.enabled=true",
        "app.outbox.initial-delay=PT24H",
        "app.rate-limit.enabled=false",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class PaymentWorkflowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.10-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private PaymentWorker paymentWorker;

    @MockitoBean
    private PaymentProvider paymentProvider;

    private UUID eventId;
    private UUID seatId;
    private String buyerToken;

    @DynamicPropertySource
    static void configureKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM outbox_events");
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
                """, eventId, "Payment Test Event", "Test Arena", now.minusHours(1), now.plusDays(1));
        jdbcTemplate.update("""
                INSERT INTO seats (id, event_id, section, row, seat_number, price_cents, currency)
                VALUES (?, ?, 'A', '1', '1', 7500, 'USD')
                """, seatId, eventId);
        buyerToken = registerAndGetToken("buyer-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void publishesAndProcessesASuccessfulPaymentExactlyOnce() throws Exception {
        UUID orderId = createPendingOrder();
        when(paymentProvider.charge(orderId)).thenReturn(PaymentResult.succeeded("mock-success-" + orderId));

        outboxPublisher.publishReadyEvents();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
            assertThat(seatStatus()).isEqualTo("SOLD");
            assertThat(paymentStatus(orderId)).isEqualTo("SUCCESS");
            assertThat(outboxPublished(orderId)).isTrue();
        });
        verify(paymentProvider).charge(orderId);

        String payload = jdbcTemplate.queryForObject("""
                SELECT payload::text
                FROM outbox_events
                WHERE aggregate_id = ?
                """, String.class, orderId);
        paymentWorker.process(payload);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Integer.class, orderId
        )).isEqualTo(1);
        verify(paymentProvider, times(1)).charge(orderId);
    }

    @Test
    void recordsAFailedPaymentAndReleasesOnlyItsOrderSeat() throws Exception {
        UUID orderId = createPendingOrder();
        when(paymentProvider.charge(orderId)).thenReturn(PaymentResult.failed("mock-declined-" + orderId));

        outboxPublisher.publishReadyEvents();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(orderStatus(orderId)).isEqualTo("FAILED");
            assertThat(seatStatus()).isEqualTo("AVAILABLE");
            assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
            assertThat(outboxPublished(orderId)).isTrue();
        });
        verify(paymentProvider).charge(orderId);
    }

    private UUID createPendingOrder() throws Exception {
        UUID holdId = createHold();
        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdIds\":[\"%s\"]}".formatted(holdId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("orderId").asText());
    }

    private UUID createHold() throws Exception {
        String response = mockMvc.perform(post("/api/events/{eventId}/seats/{seatId}/hold", eventId, seatId)
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
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

    private String orderStatus(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private String paymentStatus(UUID orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
    }

    private String seatStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId);
    }

    private boolean outboxPublished(UUID orderId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT published FROM outbox_events WHERE aggregate_id = ?", Boolean.class, orderId
        ));
    }
}
