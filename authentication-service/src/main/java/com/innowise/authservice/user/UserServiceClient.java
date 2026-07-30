package com.innowise.authservice.user;

import com.innowise.authservice.dto.UserServiceCreateRequest;
import com.innowise.authservice.dto.UserServiceResponse;
import com.innowise.authservice.exception.UserServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    public UUID createUser(UserServiceCreateRequest request) {
        try {
            UserServiceResponse response = userServiceRestClient.post()
                    .uri("/api/v1/users")
                    .body(request)
                    .retrieve()
                    .body(UserServiceResponse.class);

            if (response == null || response.id() == null) {
                throw new UserServiceException("User-service returned empty response");
            }
            return response.id();
        } catch (RestClientResponseException e) {
            throw new UserServiceException(
                    "Failed to create user in user-service: " + e.getStatusCode());
        }
    }
}