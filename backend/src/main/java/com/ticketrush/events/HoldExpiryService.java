package com.ticketrush.events;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HoldExpiryService {

    private final HoldRepository holdRepository;
    private final HoldExpiryProperties properties;
    private final SeatStatusEventPublisher seatStatusEventPublisher;

    HoldExpiryService(
            HoldRepository holdRepository,
            HoldExpiryProperties properties,
            SeatStatusEventPublisher seatStatusEventPublisher
    ) {
        this.holdRepository = holdRepository;
        this.properties = properties;
        this.seatStatusEventPublisher = seatStatusEventPublisher;
    }

    @Scheduled(
            fixedDelayString = "${app.holds.expiry.sweep-interval}",
            initialDelayString = "${app.holds.expiry.initial-delay}"
    )
    @Transactional
    public void expireDueHolds() {
        for (UUID holdId : holdRepository.findDueActiveHoldIds(properties.batchSize())) {
            if (holdRepository.expireIfDue(holdId)) {
                holdRepository.releaseSeatForInactiveHold(holdId, "EXPIRED")
                        .ifPresent(seat -> seatStatusEventPublisher.publish(
                                seat.eventId(), seat.seatId(), SeatStatus.AVAILABLE, seat.version()
                        ));
            }
        }
    }
}
