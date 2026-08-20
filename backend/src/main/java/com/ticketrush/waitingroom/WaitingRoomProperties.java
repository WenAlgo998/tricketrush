package com.ticketrush.waitingroom;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.waiting-room")
public record WaitingRoomProperties(
        boolean enabled,
        int maxActiveAdmissions,
        Duration admissionTokenTtl,
        Duration estimatedAdmissionInterval
) {

    public WaitingRoomProperties {
        if (maxActiveAdmissions < 1) {
            throw new IllegalArgumentException("app.waiting-room.max-active-admissions must be positive");
        }
        if (admissionTokenTtl == null || admissionTokenTtl.isZero() || admissionTokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.waiting-room.admission-token-ttl must be positive");
        }
        if (estimatedAdmissionInterval == null || estimatedAdmissionInterval.isZero() || estimatedAdmissionInterval.isNegative()) {
            throw new IllegalArgumentException("app.waiting-room.estimated-admission-interval must be positive");
        }
    }
}
