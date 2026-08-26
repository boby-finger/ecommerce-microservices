package com.innowise.paymentservice.event;

import com.innowise.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(
        PaymentEventType eventType,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        BigDecimal paymentAmount,
        Instant timestamp,
        Instant occurredAt
) {
}
