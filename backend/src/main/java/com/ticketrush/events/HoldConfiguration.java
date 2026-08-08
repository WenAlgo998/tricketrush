package com.ticketrush.events;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({HoldProperties.class, HoldExpiryProperties.class})
class HoldConfiguration {
}
