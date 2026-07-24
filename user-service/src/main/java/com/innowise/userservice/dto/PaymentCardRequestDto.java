package com.innowise.userservice.dto;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.LuhnCheck;


import java.time.LocalDate;

public record PaymentCardRequestDto(
        @NotBlank @Pattern(regexp = "\\d{16}") @LuhnCheck
        String cardNumber,
        @NotBlank @Size(max=100)
        String cardHolder,
        @Future @NotNull
        LocalDate expirationDate
) {
}
