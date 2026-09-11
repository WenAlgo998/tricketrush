package com.ticketrush.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
class PaymentProcessingService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final SeatStatusEventPublisher seatStatusEventPublisher;
    private final AuditLogService auditLogService;

    PaymentProcessingService(
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider,
            SeatStatusEventPublisher seatStatusEventPublisher,
            AuditLogService auditLogService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.seatStatusEventPublisher = seatStatusEventPublisher;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void process(PaymentRequested paymentRequested) {
        PaymentRepository.PendingPaymentOrder order = paymentRepository
                .findPendingOrderForUpdate(paymentRequested.orderId())
                .orElse(null);
        if (order == null) {
            return;
        }
        if (!order.userId().equals(paymentRequested.userId()) || !order.eventId().equals(paymentRequested.eventId())) {
            throw new IllegalArgumentException("Payment event does not match its pending order");
        }

        PaymentResult paymentResult = paymentProvider.charge(order.orderId());
        paymentRepository.createPayment(order.orderId(), paymentResult);

        if (paymentResult.succeeded()) {
            paymentRepository.markOrderConfirmed(order.orderId());
            publishSeatTransitions(paymentRepository.markOrderSeatsSold(order.orderId()), SeatStatus.SOLD);
            auditLogService.record("ORDER", order.orderId(), "PAYMENT_SUCCEEDED", Map.of(
                    "providerReference", paymentResult.providerReference()
            ));
            return;
        }

        paymentRepository.markOrderFailed(order.orderId());
        publishSeatTransitions(paymentRepository.releaseOrderSeats(order.orderId()), SeatStatus.AVAILABLE);
        auditLogService.record("ORDER", order.orderId(), "PAYMENT_FAILED", Map.of(
                "providerReference", paymentResult.providerReference()
        ));
    }

    private void publishSeatTransitions(List<PaymentRepository.SeatTransition> seats, SeatStatus status) {
        seats.forEach(seat -> seatStatusEventPublisher.publish(
                seat.eventId(), seat.seatId(), status, seat.version()
        ));
    }
}
