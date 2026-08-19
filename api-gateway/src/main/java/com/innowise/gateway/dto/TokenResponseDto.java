package com.innowise.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponseDto(
        String accessToken,
        String refreshToken
) {
}