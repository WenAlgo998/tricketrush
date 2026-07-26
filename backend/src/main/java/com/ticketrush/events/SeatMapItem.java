package com.ticketrush.events;

import java.util.UUID;

public record SeatMapItem(
        UUID id,
        String section,
        String row,
        String seatNumber,
        int priceCents,
        String currency,
        SeatStatus status,
        int version
) {
}
