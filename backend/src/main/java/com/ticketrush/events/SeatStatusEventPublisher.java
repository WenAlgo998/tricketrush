package com.ticketrush.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
class SeatStatusEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    SeatStatusEventPublisher(ApplicationEventPublisher applicationEventPublisher, Clock clock) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    void publish(UUID eventId, UUID seatId, SeatStatus status, int version) {
        applicationEventPublisher.publishEvent(new SeatStatusChangedEvent(
                eventId, seatId, status, version, clock.instant()
        ));
    }
}
