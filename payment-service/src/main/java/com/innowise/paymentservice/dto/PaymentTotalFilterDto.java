package com.innowise.paymentservice.dto;

import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.validation.ValidDateRange;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@ValidDateRange
public record PaymentTotalFilterDto(
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to,
        PaymentStatus status
) {
}
