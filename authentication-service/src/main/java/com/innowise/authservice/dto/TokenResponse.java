package com.innowise.authservice.dto;

public record TokenResponse(String accessToken,
                            String refreshToken) {}
