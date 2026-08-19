package com.innowise.gateway.registration;

import com.innowise.gateway.dto.CreateCredentialsRequestDto;
import com.innowise.gateway.dto.CreateProfileRequestDto;
import com.innowise.gateway.dto.RegisterRequestDto;
import com.innowise.gateway.dto.TokenResponseDto;
import com.innowise.gateway.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserServiceClient userServiceClient;
    private final AuthServiceClient authServiceClient;

    public Mono<TokenResponseDto> register(RegisterRequestDto request) {
        CreateProfileRequestDto profileRequest = new CreateProfileRequestDto(
                request.email(), request.name(), request.surname(), request.birthDate());

        return userServiceClient.createProfile(profileRequest)
                .flatMap(userId -> createCredentialsOrCompensate(request, userId));
    }

    private Mono<TokenResponseDto> createCredentialsOrCompensate(RegisterRequestDto request, UUID userId) {
        CreateCredentialsRequestDto credentialsRequest = new CreateCredentialsRequestDto(
                request.username(), request.password(), userId);

        return authServiceClient.createCredentials(credentialsRequest)
                .onErrorResume(error -> userServiceClient.deleteProfile(userId)
                        .then(Mono.error(error)));
    }
}