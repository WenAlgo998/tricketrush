package com.ticketrush.events;

public record PaymentResult(String status, String providerReference) {

    public static PaymentResult succeeded(String providerReference) {
        return new PaymentResult("SUCCESS", providerReference);
    }

    public static PaymentResult failed(String providerReference) {
        return new PaymentResult("FAILED", providerReference);
    }

    public PaymentResult {
        if (!"SUCCESS".equals(status) && !"FAILED".equals(status)) {
            throw new IllegalArgumentException("Payment status must be SUCCESS or FAILED");
        }
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Payment provider reference must not be blank");
        }
    }

    public boolean succeeded() {
        return "SUCCESS".equals(status);
    }
}
