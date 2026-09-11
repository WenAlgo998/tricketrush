package com.ticketrush.events;

import java.util.UUID;

record DeadLetteredOutboxEvent(
        UUID outboxEventId,
        UUID aggregateId,
        String eventType,
        String payload,
        int attemptCount,
        String error
) {
}
