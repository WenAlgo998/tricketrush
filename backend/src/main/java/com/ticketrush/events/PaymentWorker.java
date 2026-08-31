package com.ticketrush.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentWorker {

    private final ObjectMapper objectMapper;
    private final PaymentProcessingService paymentProcessingService;

    PaymentWorker(ObjectMapper objectMapper, PaymentProcessingService paymentProcessingService) {
        this.objectMapper = objectMapper;
        this.paymentProcessingService = paymentProcessingService;
    }

    @KafkaListener(
            topics = "${app.outbox.payment-topic}",
            groupId = "${app.payments.consumer-group}"
    )
    public void process(String payload) throws JsonProcessingException {
        paymentProcessingService.process(objectMapper.readValue(payload, PaymentRequested.class));
    }
}
