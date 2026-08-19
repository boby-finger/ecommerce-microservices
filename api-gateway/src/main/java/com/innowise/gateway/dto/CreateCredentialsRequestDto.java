package com.innowise.gateway.dto;

import java.util.UUID;

public record CreateCredentialsRequestDto(
        String username,
        String password,
        UUID userId
) {
}