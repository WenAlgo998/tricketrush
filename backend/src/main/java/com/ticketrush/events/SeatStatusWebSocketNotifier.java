package com.ticketrush.events;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
class SeatStatusWebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    SeatStatusWebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void notifySubscribers(SeatStatusChangedEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/events/" + event.eventId() + "/seats",
                new SeatStatusMessage(event.seatId(), event.status().name(), event.timestamp(), event.version())
        );
    }

    record SeatStatusMessage(UUID seatId, String status, java.time.Instant timestamp, int version) {
    }
}
