package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;

    CheckoutService(CheckoutRepository checkoutRepository) {
        this.checkoutRepository = checkoutRepository;
    }

    @Transactional
    public PendingOrder checkout(UUID userId, UUID idempotencyKey, List<UUID> holdIds) {
        CheckoutRepository.OrderSummary existingOrder = checkoutRepository
                .findOrderByIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (existingOrder != null) {
            return PendingOrder.from(existingOrder);
        }

        if (holdIds.isEmpty() || new HashSet<>(holdIds).size() != holdIds.size()) {
            throw new IllegalArgumentException("holdIds must contain unique values");
        }

        List<CheckoutRepository.HoldEvent> holds = checkoutRepository.findHoldEvents(userId, holdIds);
        if (holds.size() != holdIds.size()) {
            throw new CheckoutHoldConflictException();
        }
        Set<UUID> eventIds = holds.stream().map(CheckoutRepository.HoldEvent::eventId).collect(java.util.stream.Collectors.toSet());
        if (eventIds.size() != 1) {
            throw new CheckoutHoldConflictException();
        }

        UUID orderId = UUID.randomUUID();
        UUID eventId = eventIds.iterator().next();
        if (!checkoutRepository.createPendingOrder(orderId, userId, eventId, idempotencyKey)) {
            return PendingOrder.from(checkoutRepository.findOrderByIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Idempotent order was not found")));
        }

        for (UUID holdId : holdIds) {
            CheckoutRepository.ConsumedHold hold = checkoutRepository.consumeActiveHold(holdId, userId)
                    .orElseThrow(CheckoutHoldConflictException::new);
            checkoutRepository.createOrderSeat(orderId, hold.seatId());
        }
        return new PendingOrder(orderId, "PENDING");
    }

    public record PendingOrder(UUID orderId, String status) {
        private static PendingOrder from(CheckoutRepository.OrderSummary order) {
            return new PendingOrder(order.orderId(), order.status());
        }
    }
}
