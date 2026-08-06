package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class HoldService {

    private final EventCatalogRepository eventCatalogRepository;
    private final HoldRepository holdRepository;
    private final HoldProperties holdProperties;
    private final Clock clock;

    HoldService(
            EventCatalogRepository eventCatalogRepository,
            HoldRepository holdRepository,
            HoldProperties holdProperties,
            Clock clock
    ) {
        this.eventCatalogRepository = eventCatalogRepository;
        this.holdRepository = holdRepository;
        this.holdProperties = holdProperties;
        this.clock = clock;
    }

    @Transactional
    public CreatedHold create(UUID eventId, UUID seatId, UUID userId, int expectedVersion) {
        EventCatalogItem event = eventCatalogRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        if (event.status() != EventStatus.ON_SALE || event.saleStartAt().toInstant().isAfter(clock.instant())) {
            throw new EventNotOnSaleException();
        }

        if (!holdRepository.markSeatHeldIfAvailable(eventId, seatId, expectedVersion)) {
            throw new SeatHoldConflictException();
        }

        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                clock.instant().plus(holdProperties.duration()), ZoneOffset.UTC
        );
        UUID holdId = UUID.randomUUID();
        holdRepository.createActiveHold(holdId, seatId, userId, expiresAt);
        return new CreatedHold(holdId, expiresAt);
    }

    public record CreatedHold(UUID holdId, OffsetDateTime expiresAt) {
    }
}
