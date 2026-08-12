package com.innowise.orderservice.flow;

import com.innowise.orderservice.IntegrationTestBase;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import com.github.tomakehurst.wiremock.client.WireMock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderFlowIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "vasya@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private Item laptop;
    private Item mouse;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        laptop = saveItem("Laptop", new BigDecimal("1200.50"));
        mouse = saveItem("Mouse", new BigDecimal("25.00"));

        userId = UUID.randomUUID();
        stubUserByEmail(userId);
        stubUserById(userId);
    }

    private Item saveItem(String name, BigDecimal price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(price);
        return itemRepository.save(item);
    }

    private String userJson(UUID id) {
        return """
                {
                  "id": "%s",
                  "email": "%s",
                  "name": "Vasya",
                  "surname": "Pupkin",
                  "birthDate": "1999-01-01"
                }
                """.formatted(id, EMAIL);
    }

    private void stubUserByEmail(UUID id) {
        USER_SERVICE.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo(OrderFlowIntegrationTest.EMAIL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(id))));
    }

    private void stubUserById(UUID id) {
        USER_SERVICE.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/" + id))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(id))));
    }

    private String orderJson(UUID itemId, int quantity) {
        return """
                {
                  "userEmail": "%s",
                  "items": [
                    {"itemId": "%s", "quantity": %d}
                  ]
                }
                """.formatted(EMAIL, itemId, quantity);
    }

    private String createOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(laptop.getId(), 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*?\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    @Test
    void createOrderReturnsCreatedWithUserInfo() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(laptop.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.available").value(true))
                .andExpect(jsonPath("$.totalPrice").value(2401.00));
    }

    @Test
    void createOrderCallsUserServiceWithInternalKey() throws Exception {
        createOrder();

        USER_SERVICE.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo(EMAIL))
                .withHeader("X-Internal-Api-Key", equalTo("test-internal-key")));
    }

    @Test
    void createOrderWithUnknownEmailIsUnprocessable() throws Exception {
        USER_SERVICE.stubFor(WireMock.get(urlPathEqualTo("/api/v1/users/by-email"))
                .withQueryParam("email", equalTo("nobody@example.com"))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "nobody@example.com",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(laptop.getId())))
                .andExpect(status().isUnprocessableEntity());

        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrderWithUnknownItemIsUnprocessable() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(UUID.randomUUID(), 1)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createOrderWithInvalidEmailIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "not-an-email",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(laptop.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.userEmail").exists());

        verifyNoUserServiceCalls();
    }

    @Test
    void createOrderWithNoItemsIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "%s", "items": []}
                                """.formatted(EMAIL)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrderWithZeroQuantityIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(laptop.getId(), 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrderWithDuplicateItemsIsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userEmail": "%s",
                                 "items": [{"itemId": "%s", "quantity": 1},
                                           {"itemId": "%s", "quantity": 2}]}
                                """.formatted(EMAIL, laptop.getId(), laptop.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void getOrderByIdReturnsItemsAndUser() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].item.name").value("Laptop"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.user.name").value("Vasya"));
    }

    @Test
    void getMissingOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderWithMalformedIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchFiltersByStatus() throws Exception {
        createOrder();

        mockMvc.perform(get("/api/v1/orders").param("statuses", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/orders").param("statuses", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchFiltersByCreationDate() throws Exception {
        createOrder();

        mockMvc.perform(get("/api/v1/orders")
                        .param("createdFrom", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/orders")
                        .param("createdTo", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchFiltersByUserId() throws Exception {
        createOrder();

        mockMvc.perform(get("/api/v1/orders").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/orders").param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void searchWithUnknownStatusIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("statuses", "BANANA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchIsPaginated() throws Exception {
        createOrder();

        mockMvc.perform(get("/api/v1/orders").param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getOrdersByUserId() throws Exception {
        createOrder();

        mockMvc.perform(get("/api/v1/orders/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void updateOrderReplacesItems() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAID",
                                 "items": [{"itemId": "%s", "quantity": 4}]}
                                """.formatted(mouse.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPrice").value(100.00));
    }

    @Test
    void updateOrderKeepingTheSameItem() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAID",
                                 "items": [{"itemId": "%s", "quantity": 5}]}
                                """.formatted(laptop.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void updateDeliveredOrderIsConflict() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(laptop.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PAID",
                                 "items": [{"itemId": "%s", "quantity": 1}]}
                                """.formatted(laptop.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteOrderIsSoft() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from order_items where order_id = ?", Integer.class,
                UUID.fromString(orderId))).isEqualTo(1);
    }

    @Test
    void deletedOrderIsHiddenFromSearch() throws Exception {
        String orderId = createOrder();
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void deleteTwiceIsNotFound() throws Exception {
        String orderId = createOrder();

        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)).andExpect(status().isNotFound());
    }

    @Test
    void deleteDoesNotCallUserService() throws Exception {
        String orderId = createOrder();
        USER_SERVICE.resetRequests();

        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)).andExpect(status().isNoContent());

        USER_SERVICE.verify(0, getRequestedFor(urlPathMatching("/api/v1/users.*")));
    }
}