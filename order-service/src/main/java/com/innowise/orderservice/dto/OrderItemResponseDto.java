package com.innowise.orderservice.dto;

import java.util.UUID;

public record OrderItemResponseDto(
        UUID id,
        ItemResponseDto item,
        Integer quantity
) {
}