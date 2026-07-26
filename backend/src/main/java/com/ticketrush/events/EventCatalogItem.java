package com.ticketrush.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventCatalogItem(
        UUID id,
        String name,
        String venueName,
        OffsetDateTime saleStartAt,
        OffsetDateTime eventStartAt,
        EventStatus status
) {
}
