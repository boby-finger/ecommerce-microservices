package com.innowise.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public record ValidateResponseDto(
        boolean valid,
        String username,
        UUID userId,
        String role,
        Instant expiresAt,
        String reason
) {
    public static ValidateResponseDto invalid(String reason) {
        return new ValidateResponseDto(false, null, null, null, null, reason);
    }
}