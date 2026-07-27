package com.ticketrush.auth;

public record JwtToken(String value, long expiresInSeconds) {
}
