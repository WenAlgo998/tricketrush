package com.ticketrush.events;

import java.util.UUID;

public interface PaymentProvider {

    PaymentResult charge(UUID orderId);
}
