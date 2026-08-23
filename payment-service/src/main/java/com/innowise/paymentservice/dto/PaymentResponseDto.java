package com.innowise.paymentservice.dto;

import com.innowise.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponseDto(
        UUID id,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        Instant timestamp,
        BigDecimal paymentAmount
) {
}
