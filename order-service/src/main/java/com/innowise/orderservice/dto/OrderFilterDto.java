package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderFilterDto(
        UUID userId,
        Instant createdFrom,
        Instant createdTo,
        List<OrderStatus> statuses
) {
}