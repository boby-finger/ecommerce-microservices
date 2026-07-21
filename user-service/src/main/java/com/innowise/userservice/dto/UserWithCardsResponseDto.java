package com.innowise.userservice.dto;

import com.innowise.userservice.model.PaymentCard;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserWithCardsResponseDto(
        UUID id,
        String email,
        String name,
        String surname,
        LocalDate birthDate,
        boolean active,
        List<PaymentCardResponse> cards
) {
}
