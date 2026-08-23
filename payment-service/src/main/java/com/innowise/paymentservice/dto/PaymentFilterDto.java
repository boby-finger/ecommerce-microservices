package com.innowise.paymentservice.dto;

import com.innowise.paymentservice.model.PaymentStatus;

import java.util.UUID;

public record PaymentFilterDto(
        UUID userId,
        UUID orderId,
        PaymentStatus status
) {

    public PaymentFilterDto scopedTo(UUID ownerId) {
        return new PaymentFilterDto(ownerId, orderId, status);
    }

    public boolean isEmpty() {
        return userId == null && orderId == null && status == null;
    }
}
