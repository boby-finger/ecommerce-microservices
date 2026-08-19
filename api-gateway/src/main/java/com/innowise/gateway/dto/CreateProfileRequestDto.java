package com.innowise.gateway.dto;

import java.time.LocalDate;

public record CreateProfileRequestDto(
        String email,
        String name,
        String surname,
        LocalDate birthDate
) {
}