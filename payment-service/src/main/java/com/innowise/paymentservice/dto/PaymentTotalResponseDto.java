package com.innowise.paymentservice.dto;

import com.innowise.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTotalResponseDto(
        UUID userId,
        PaymentStatus status,
        Instant from,
        Instant to,
        BigDecimal total,
        long count
) {
}
