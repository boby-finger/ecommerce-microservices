package com.innowise.authservice.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegisterRequestDto(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String surname,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email String email
) {}