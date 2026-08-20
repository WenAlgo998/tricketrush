package com.ticketrush.waitingroom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WaitingRoomProperties.class)
class WaitingRoomConfiguration {
}
