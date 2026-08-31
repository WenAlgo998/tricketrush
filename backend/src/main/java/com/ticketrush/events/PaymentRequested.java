package com.ticketrush.events;

import java.util.UUID;

public record PaymentRequested(UUID orderId, UUID userId, UUID eventId) {
}
