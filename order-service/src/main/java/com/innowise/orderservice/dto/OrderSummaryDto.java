package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryDto(
        UUID id,
        UUID userId,
        UserInfoDto user,
        OrderStatus status,
        BigDecimal totalPrice,
        Instant createdAt
) {
}