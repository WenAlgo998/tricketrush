package com.ticketrush.auth;

import java.util.UUID;

public record UserAccount(UUID id, String email, String passwordHash) {
}
