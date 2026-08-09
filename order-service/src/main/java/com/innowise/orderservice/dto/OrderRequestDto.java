package com.innowise.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderRequestDto(
        @NotNull
        UUID userId,
        @NotEmpty @Valid
        List<OrderItemRequestDto> items
) {
}