package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderUpdateRequestDto(
        @NotNull
        OrderStatus status,
        @NotEmpty @Valid
        List<OrderItemRequestDto> items
) {
}