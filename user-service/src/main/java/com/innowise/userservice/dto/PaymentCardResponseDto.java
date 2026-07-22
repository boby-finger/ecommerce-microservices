package com.innowise.userservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentCardResponseDto(
        UUID id,
        String cardNumber,
        String cardHolder,
        LocalDate expirationDate,
        boolean active
) {
}
