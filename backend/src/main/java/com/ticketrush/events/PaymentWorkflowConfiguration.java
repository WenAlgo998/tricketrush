package com.ticketrush.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
class PaymentWorkflowConfiguration {

    @Bean
    NewTopic paymentEventsTopic(OutboxProperties properties) {
        return TopicBuilder.name(properties.paymentTopic())
                .partitions(1)
                .replicas(1)
                .build();
    }
}
