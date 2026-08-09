package com.ticketrush.events;

import java.time.Instant;
import java.util.UUID;

public record SeatStatusChangedEvent(
        UUID eventId,
        UUID seatId,
        SeatStatus status,
        int version,
        Instant timestamp
) {
}
