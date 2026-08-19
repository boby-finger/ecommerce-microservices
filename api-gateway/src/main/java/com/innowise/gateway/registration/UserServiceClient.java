package com.innowise.gateway.registration;

import com.innowise.gateway.dto.CreateProfileRequestDto;
import com.innowise.gateway.dto.ProfileResponseDto;
import com.innowise.gateway.exception.RegistrationFailedException;
import com.innowise.gateway.exception.UsernameAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder builder,
                             @Value("${services.user.url}") String userServiceUrl,
                             @Value("${services.internal-api-key}") String internalApiKey) {
        this.webClient = builder
                .baseUrl(userServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public Mono<UUID> createProfile(CreateProfileRequestDto request) {
        return webClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status == HttpStatus.CONFLICT, response ->
                        Mono.error(new UsernameAlreadyExistsException("email")))
                .bodyToMono(ProfileResponseDto.class)
                .map(ProfileResponseDto::id)
                .onErrorMap(
                        e -> e instanceof WebClientException
                                && !(e instanceof UsernameAlreadyExistsException),
                        e -> new RegistrationFailedException(
                                "user-service is unavailable: " + e.getMessage(), e));
    }

    public Mono<Void> deleteProfile(UUID userId) {
        return webClient.delete()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .toBodilessEntity()
                .doOnNext(response -> log.info("Compensation: profile {} removed", userId))
                .then()
                .onErrorResume(WebClientResponseException.NotFound.class, notFound -> {
                    log.info("Compensation: profile {} was already absent", userId);
                    return Mono.empty();
                })
                .onErrorResume(error -> {
                    log.error("ORPHANED PROFILE: could not roll back profile {} after a failed "
                            + "registration. It exists in user-service with no credentials and "
                            + "needs manual or scheduled cleanup.", userId, error);
                    return Mono.empty();
                });
    }
}