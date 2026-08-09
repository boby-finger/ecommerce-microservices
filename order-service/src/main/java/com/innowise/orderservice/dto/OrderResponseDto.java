package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
        UUID id,
        UUID userId,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemResponseDto> items,
        Instant createdAt,
        Instant updatedAt
) {
}