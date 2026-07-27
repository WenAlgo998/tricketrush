package com.ticketrush.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.auth.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl) {
}
