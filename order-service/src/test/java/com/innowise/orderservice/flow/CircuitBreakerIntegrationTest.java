package com.innowise.orderservice.flow;

import com.innowise.orderservice.IntegrationTestBase;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What happens to order-service when user-service misbehaves. The test config narrows the
 * window to four calls so the breaker trips quickly; the behaviour under test is the
 * mechanism, not the production tuning.
 */
class CircuitBreakerIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "vasya@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    private UUID userId;
    private UUID orderId;
    private Item laptop;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        laptop = new Item();
        laptop.setName("Laptop");
        laptop.setPrice(new BigDecimal("1200.50"));
        laptop = itemRepository.save(laptop);

        userId = UUID.randomUUID();
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalPrice(new BigDecimal("1200.50"));
        order.setDeleted(false);
        OrderItem line = new OrderItem();
        line.setItem(laptop);
        line.setQuantity(1);
        order.addItem(line);
        orderId = orderRepository.save(order).getId();
    }

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("user-service");
    }

    private void stubUserServiceFailing() {
        USER_SERVICE.stubFor(get(urlPathMatching("/api/v1/users.*"))
                .willReturn(aResponse().withStatus(500)));
    }

    @Test
    void readDegradesInsteadOfFailing() throws Exception {
        stubUserServiceFailing();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(1200.50))
                .andExpect(jsonPath("$.user.available").value(false))
                .andExpect(jsonPath("$.user.email").doesNotExist());
    }

    @Test
    void repeatedFailuresOpenTheBreaker() throws Exception {
        stubUserServiceFailing();

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isOk());
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void openBreakerStopsCallingTheRemoteService() throws Exception {
        stubUserServiceFailing();

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/orders/{id}", orderId)).andExpect(status().isOk());
        }
        USER_SERVICE.resetRequests();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.available").value(false));

        USER_SERVICE.verify(0, getRequestedFor(urlPathMatching("/api/v1/users.*")));
    }

    @Test
    void createIsRejectedWhileTheBreakerIsOpen() throws Exception {
        stubUserServiceFailing();
        breaker().transitionToOpenState();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "%s",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(EMAIL, laptop.getId())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void createReportsUnavailableRatherThanServerError() throws Exception {
        stubUserServiceFailing();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "%s",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(EMAIL, laptop.getId())))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unknownUserDoesNotOpenTheBreaker() throws Exception {
        USER_SERVICE.stubFor(get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo(EMAIL))
                .willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userEmail": "%s",
                                     "items": [{"itemId": "%s", "quantity": 1}]}
                                    """.formatted(EMAIL, laptop.getId())))
                    .andExpect(status().isUnprocessableEntity());
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void breakerClosesAgainAfterRecovery() throws Exception {
        USER_SERVICE.stubFor(get(urlPathEqualTo("/api/v1/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "%s",
                                  "email": "%s",
                                  "name": "Vasya",
                                  "surname": "Pupkin",
                                  "birthDate": "1999-01-01"
                                }
                                """.formatted(userId, EMAIL))));

        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
        breaker().transitionToClosedState();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.available").value(true))
                .andExpect(jsonPath("$.user.email").value(EMAIL));
    }
}