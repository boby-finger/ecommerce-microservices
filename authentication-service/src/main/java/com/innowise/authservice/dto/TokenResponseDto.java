package com.innowise.authservice.dto;

public record TokenResponseDto(String accessToken,
                               String refreshToken) {}
