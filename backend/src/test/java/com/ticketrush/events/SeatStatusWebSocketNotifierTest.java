package com.ticketrush.events;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeatStatusWebSocketNotifierTest {

    @Test
    void publishesTheCommittedSeatStateToTheEventTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        SeatStatusWebSocketNotifier notifier = new SeatStatusWebSocketNotifier(messagingTemplate);
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-09T12:00:00Z");

        notifier.notifySubscribers(new SeatStatusChangedEvent(
                eventId, seatId, SeatStatus.HELD, 4, timestamp
        ));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/events/" + eventId + "/seats"), payload.capture()
        );
        SeatStatusWebSocketNotifier.SeatStatusMessage message =
                (SeatStatusWebSocketNotifier.SeatStatusMessage) payload.getValue();
        assertThat(message.seatId()).isEqualTo(seatId);
        assertThat(message.status()).isEqualTo("HELD");
        assertThat(message.version()).isEqualTo(4);
        assertThat(message.timestamp()).isEqualTo(timestamp);
    }
}
