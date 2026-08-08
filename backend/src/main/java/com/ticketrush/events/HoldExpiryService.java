package com.ticketrush.events;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HoldExpiryService {

    private final HoldRepository holdRepository;
    private final HoldExpiryProperties properties;

    HoldExpiryService(HoldRepository holdRepository, HoldExpiryProperties properties) {
        this.holdRepository = holdRepository;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${app.holds.expiry.sweep-interval}",
            initialDelayString = "${app.holds.expiry.initial-delay}"
    )
    @Transactional
    public void expireDueHolds() {
        for (UUID holdId : holdRepository.findDueActiveHoldIds(properties.batchSize())) {
            if (holdRepository.expireIfDue(holdId)) {
                holdRepository.releaseSeatForInactiveHold(holdId, "EXPIRED");
            }
        }
    }
}
