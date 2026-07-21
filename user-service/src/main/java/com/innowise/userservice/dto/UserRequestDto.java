package com.innowise.userservice.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UserRequestDto(
        @Email @NotBlank //чтобы в строке было хоть что то
        String email,
        @Size(max=100) @NotBlank
        String name,
        @Size(max = 100) @NotBlank
        String surname,
        @Past @NotNull //все кроме null для дат, объектов и чисел
        LocalDate birthDate
){}
