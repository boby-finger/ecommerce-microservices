package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserInfoDto;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    public static final String CIRCUIT_BREAKER = "user-service";

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = CIRCUIT_BREAKER)
    public UserInfoDto getUserByEmail(String email) {
        UserResponse response = userServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/by-email")
                        .queryParam("email", email)
                        .build())
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, clientResponse) -> {
                    throw new UserNotFoundException(email);
                })
                .body(UserResponse.class);

        if (response == null) {
            throw new UserServiceUnavailableException();
        }
        return toDto(response);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getUserByIdFallback")
    public UserInfoDto getUserById(UUID userId) {
        UserResponse response = userServiceRestClient.get()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .body(UserResponse.class);

        return response == null ? UserInfoDto.unavailable(userId) : toDto(response);
    }

    @SuppressWarnings("unused")
    private UserInfoDto getUserByIdFallback(UUID userId, Throwable throwable) {
        log.warn("user-service is unavailable, returning order without user info for {}: {}",
                userId, throwable.toString());
        return UserInfoDto.unavailable(userId);
    }

    private UserInfoDto toDto(UserResponse response) {
        return UserInfoDto.of(response.id(), response.email(), response.name(),
                response.surname(), response.birthDate());
    }

    private record UserResponse(
            UUID id,
            String email,
            String name,
            String surname,
            java.time.LocalDate birthDate
    ) {
    }
}