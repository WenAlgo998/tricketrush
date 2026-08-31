package com.ticketrush.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties properties;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            OutboxProperties properties,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.publish-interval}",
            initialDelayString = "${app.outbox.initial-delay}"
    )
    @Transactional
    public void publishReadyEvents() {
        for (OutboxEventRepository.OutboxEvent event : outboxEventRepository.lockReadyPaymentEvents(properties.batchSize())) {
            publish(event);
            outboxEventRepository.markPublished(event.id());
        }
    }

    private void publish(OutboxEventRepository.OutboxEvent event) {
        try {
            kafkaTemplate.send(properties.paymentTopic(), event.aggregateId().toString(), event.payload()).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to publish outbox event", exception.getCause());
        }
    }
}
