package com.innowise.userservice.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.LuhnCheck;
import org.hibernate.validator.constraints.UUID;

import java.time.LocalDate;

public record PaymentCardRequestDto(
        @NotBlank @UUID
        UUID userId,
        @NotBlank @LuhnCheck
        String cardNumber,
        @NotBlank @Size(max=100)
        String cardHolder,
        @Future @NotNull
        LocalDate expirationDate
) {
}
