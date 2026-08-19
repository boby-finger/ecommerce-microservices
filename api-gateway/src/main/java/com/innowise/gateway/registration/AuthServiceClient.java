package com.innowise.gateway.registration;

import com.innowise.gateway.dto.CreateCredentialsRequestDto;
import com.innowise.gateway.dto.TokenResponseDto;
import com.innowise.gateway.exception.RegistrationFailedException;
import com.innowise.gateway.exception.UsernameAlreadyExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class AuthServiceClient {

    private final WebClient webClient;

    public AuthServiceClient(WebClient.Builder builder,
                             @Value("${services.auth.url}") String authServiceUrl,
                             @Value("${services.internal-api-key}") String internalApiKey) {
        this.webClient = builder
                .baseUrl(authServiceUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public Mono<TokenResponseDto> createCredentials(CreateCredentialsRequestDto request) {
        return webClient.post()
                .uri("/api/v1/auth/internal/credentials")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status == HttpStatus.CONFLICT, response ->
                        Mono.error(new UsernameAlreadyExistsException("username")))
                .bodyToMono(TokenResponseDto.class)
                .onErrorMap(
                        e -> e instanceof WebClientException
                                && !(e instanceof UsernameAlreadyExistsException),
                        e -> new RegistrationFailedException(
                                "auth-service is unavailable: " + e.getMessage(), e));
    }
}