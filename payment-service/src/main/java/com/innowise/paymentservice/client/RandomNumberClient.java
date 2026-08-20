package com.innowise.paymentservice.client;

import com.innowise.paymentservice.exception.RandomNumberUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

@Slf4j
@Component
public class RandomNumberClient {

    private final RestClient randomNumberRestClient;
    private final URI apiUri;

    public RandomNumberClient(RestClient randomNumberRestClient,
                              @Value("${random-number.url}") String url) {
        this.randomNumberRestClient = randomNumberRestClient;
        this.apiUri = URI.create(url);
    }

    public int nextNumber() {
        log.info("Requesting a random number from {}", apiUri);

        String body;
        try {
            body = randomNumberRestClient.get()
                    .uri(apiUri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("Random number request to {} failed: {}", apiUri, e.toString());
            throw new RandomNumberUnavailableException(e.getMessage(), e);
        }

        if (body == null || body.isBlank()) {
            log.warn("Random number request to {} returned an empty body", apiUri);
            throw new RandomNumberUnavailableException("empty response body");
        }

        String trimmed = body.trim();
        try {
            int number = Integer.parseInt(trimmed);
            log.info("Random number provider returned {}", number);
            return number;
        } catch (NumberFormatException e) {
            log.warn("Random number request to {} returned an unparseable body: '{}'", apiUri, trimmed);
            throw new RandomNumberUnavailableException("unparseable response body", e);
        }
    }
}
