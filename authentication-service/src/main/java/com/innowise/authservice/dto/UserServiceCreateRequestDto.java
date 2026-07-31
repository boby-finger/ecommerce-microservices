package com.innowise.authservice.dto;

import java.time.LocalDate;

// пока что буду дублировать UserRequestDto
// мб потом сделать OpenAPI-спецификацию с генерацией клиента
public record UserServiceCreateRequestDto(
        String email,
        String name,
        String surname,
        LocalDate birthDate
) {}