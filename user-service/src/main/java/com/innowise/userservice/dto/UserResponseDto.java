package com.innowise.userservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String email,
        String name,
        String surname,
        LocalDate birthDate,
        boolean active
) {
}
