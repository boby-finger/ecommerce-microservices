package com.innowise.orderservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequestDto(
        @NotNull
        UUID itemId,
        @NotNull @Min(1) @Max(1000)
        Integer quantity
) {
}