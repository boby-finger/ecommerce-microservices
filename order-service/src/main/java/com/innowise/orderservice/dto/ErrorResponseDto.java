package com.innowise.orderservice.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponseDto(
        int status,
        String error,
        Instant timestamp,
        Map<String, String> validationErrors
) {
    public ErrorResponseDto(int status, String error) {
        this(status, error, Instant.now(), null);
    }
}