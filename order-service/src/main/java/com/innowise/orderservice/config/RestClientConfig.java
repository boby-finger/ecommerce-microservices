package com.innowise.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Timeouts are the point of this bean. Without them a hung user-service holds this
     * service's threads open indefinitely, and the circuit breaker never trips because
     * nothing ever fails — it just waits.
     * <p>
     * The shared service key is attached here once: user-service is a resource server and
     * rejects anonymous calls, and order-service has no end user token to forward.
     */
    @Bean
    public RestClient userServiceRestClient(
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${user-service.internal-api-key}") String internalApiKey) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(requestFactory)
                .build();
    }
}