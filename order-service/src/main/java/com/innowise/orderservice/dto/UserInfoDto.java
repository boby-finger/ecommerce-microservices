package com.innowise.orderservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UserInfoDto(
        UUID id,
        String email,
        String name,
        String surname,
        LocalDate birthDate,
        boolean available
) {

    public static UserInfoDto of(UUID id, String email, String name, String surname, LocalDate birthDate) {
        return new UserInfoDto(id, email, name, surname, birthDate, true);
    }

    public static UserInfoDto unavailable(UUID id) {
        return new UserInfoDto(id, null, null, null, null, false);
    }
}