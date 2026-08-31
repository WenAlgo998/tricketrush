package com.ticketrush.events;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class MockPaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult charge(UUID orderId) {
        return PaymentResult.succeeded("mock-payment-" + orderId);
    }
}
