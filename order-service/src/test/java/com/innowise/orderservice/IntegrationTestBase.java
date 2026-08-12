package com.innowise.orderservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    protected static final WireMockServer USER_SERVICE = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        USER_SERVICE.start();
    }

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("user-service.base-url", USER_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetSharedState() {
        USER_SERVICE.resetAll();
        circuitBreakerRegistry.circuitBreaker("user-service").reset();
    }

    protected static void verifyNoUserServiceCalls() {
        USER_SERVICE.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }
}