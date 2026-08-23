package com.innowise.paymentservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String DATABASE = "payment_db";
    protected static final String RANDOM_NUMBER_PATH = "/integers";

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:7");

    protected static final WireMockServer RANDOM_NUMBER_API =
            new WireMockServer(options().dynamicPort());

    static {
        MONGO.start();
        RANDOM_NUMBER_API.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/" + DATABASE);
        registry.add("random-number.url", () -> RANDOM_NUMBER_API.baseUrl() + RANDOM_NUMBER_PATH);
    }

    @BeforeEach
    void resetRandomNumberApi() {
        RANDOM_NUMBER_API.resetAll();
    }
}
