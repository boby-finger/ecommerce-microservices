package com.innowise.paymentservice.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
        @NotNull
        UUID orderId,
        @NotNull
        UUID userId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal paymentAmount
) {
}
