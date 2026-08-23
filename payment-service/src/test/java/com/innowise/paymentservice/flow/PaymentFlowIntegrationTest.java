package com.innowise.paymentservice.flow;

import com.innowise.paymentservice.IntegrationTestBase;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentFlowIntegrationTest extends IntegrationTestBase {

    private static final String FROM = "2026-01-01T00:00:00Z";
    private static final String TO = "2026-01-31T23:59:59Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID userId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void evenRandomNumberProducesSuccess() throws Exception {
        stubRandomNumber("42");

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(paymentRepository.findAll())
                .singleElement()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void oddRandomNumberProducesFailed() throws Exception {
        stubRandomNumber("43");

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(paymentRepository.findAll())
                .singleElement()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void unavailableProviderReturns503AndLeavesPending() throws Exception {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse().withStatus(500)));

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.05")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("PENDING")));

        assertThat(paymentRepository.findAll())
                .singleElement()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void slowProviderHitsTimeout() throws Exception {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse().withFixedDelay(4000).withBody("2")));

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.05")))
                .andExpect(status().isServiceUnavailable());

        assertThat(paymentRepository.findAll())
                .singleElement()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void unparseableProviderResponseReturns503() throws Exception {
        stubRandomNumber("<html>Error 502</html>");

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.05")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void createPaymentValidatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.paymentAmount").isNotEmpty());

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.orderId").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.userId").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.paymentAmount").isNotEmpty());

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "not-a-uuid",
                                  "userId": "%s",
                                  "paymentAmount": 10.00
                                }
                                """.formatted(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(orderId, userId, "10.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.paymentAmount").isNotEmpty());

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void searchByEachSingleFilter() throws Exception {
        save(userId, orderId, PaymentStatus.SUCCESS, "10.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.FAILED, "5.00", Instant.parse("2026-01-11T12:00:00Z"));
        save(UUID.randomUUID(), orderId, PaymentStatus.PENDING, "7.00", Instant.parse("2026-01-12T12:00:00Z"));

        mockMvc.perform(searchRequest().param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(searchRequest().param("orderId", orderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(searchRequest().param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentAmount").value(5.00));
    }

    @Test
    void undeterminedStatusIsAcceptedByApi() throws Exception {
        save(userId, orderId, PaymentStatus.UNDETERMINED, "13.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, orderId, PaymentStatus.SUCCESS, "10.00", Instant.parse("2026-01-11T12:00:00Z"));

        mockMvc.perform(searchRequest().param("status", "UNDETERMINED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("UNDETERMINED"))
                .andExpect(jsonPath("$.content[0].paymentAmount").value(13.00));

        String total = mockMvc.perform(totalRequest("/api/v1/payments/total", asAdmin())
                        .param("status", "UNDETERMINED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDETERMINED"))
                .andExpect(jsonPath("$.count").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(total).contains("\"total\":13.00");

        mockMvc.perform(searchRequest().param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"));
    }

    @Test
    void searchRequiresAtLeastOneFilter() throws Exception {
        mockMvc.perform(searchRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(Matchers.containsString("At least one filter")));
    }

    @Test
    void searchIsPagedAndSortedByTimestampDesc() throws Exception {
        save(userId, orderId, PaymentStatus.SUCCESS, "1.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, orderId, PaymentStatus.SUCCESS, "2.00", Instant.parse("2026-01-11T12:00:00Z"));
        save(userId, orderId, PaymentStatus.SUCCESS, "3.00", Instant.parse("2026-01-12T12:00:00Z"));

        mockMvc.perform(searchRequest()
                        .param("userId", userId.toString())
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].paymentAmount").value(3.00))
                .andExpect(jsonPath("$.content[1].paymentAmount").value(2.00));

        mockMvc.perform(searchRequest()
                        .param("userId", userId.toString())
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paymentAmount").value(1.00));
    }

    @Test
    void totalForCurrentUserIsExact() throws Exception {
        save(userId, orderId, PaymentStatus.SUCCESS, "0.10", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, orderId, PaymentStatus.SUCCESS, "0.20", Instant.parse("2026-01-11T12:00:00Z"));
        save(userId, orderId, PaymentStatus.SUCCESS, "10.05", Instant.parse("2026-01-12T12:00:00Z"));
        save(UUID.randomUUID(), orderId, PaymentStatus.SUCCESS, "99.99", Instant.parse("2026-01-13T12:00:00Z"));

        String body = mockMvc.perform(totalRequest("/api/v1/payments/total/me", asUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.count").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"total\":10.35");
    }

    @Test
    void totalForAllUsersRespectsStatus() throws Exception {
        save(userId, orderId, PaymentStatus.SUCCESS, "10.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(UUID.randomUUID(), orderId, PaymentStatus.SUCCESS, "7.50", Instant.parse("2026-01-11T12:00:00Z"));
        save(UUID.randomUUID(), orderId, PaymentStatus.FAILED, "100.00", Instant.parse("2026-01-12T12:00:00Z"));

        String all = mockMvc.perform(totalRequest("/api/v1/payments/total", asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(all).contains("\"total\":117.50");

        String successOnly = mockMvc.perform(totalRequest("/api/v1/payments/total", asAdmin())
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.count").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(successOnly).contains("\"total\":17.50");
    }

    @Test
    void totalValidatesDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/payments/total")
                        .param("from", TO).param("to", FROM).with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.from")
                        .value(Matchers.containsString("later than")));

        mockMvc.perform(get("/api/v1/payments/total").param("from", FROM).with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.to").isNotEmpty());

        mockMvc.perform(get("/api/v1/payments/total")
                        .param("from", "not-a-date").param("to", TO).with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.from").isNotEmpty());
    }

    private void stubRandomNumber(String body) {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(body + "\n")));
    }

    private MockHttpServletRequestBuilder searchRequest() {
        return get("/api/v1/payments").with(asAdmin());
    }

    private MockHttpServletRequestBuilder totalRequest(String path, RequestPostProcessor caller) {
        return get(path).param("from", FROM).param("to", TO).with(caller);
    }

    private static String paymentJson(UUID order, UUID user, String amount) {
        return """
                {
                  "orderId": "%s",
                  "userId": "%s",
                  "paymentAmount": %s
                }
                """.formatted(order, user, amount);
    }

    private void save(UUID user, UUID order, PaymentStatus status, String amount, Instant timestamp) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order);
        payment.setUserId(user);
        payment.setStatus(status);
        payment.setPaymentAmount(new BigDecimal(amount));
        payment.setTimestamp(timestamp);
        paymentRepository.insert(payment);
    }

    private RequestPostProcessor asUser(UUID id) {
        return jwt()
                .jwt(builder -> builder.claim("userId", id.toString()).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor asAdmin() {
        return jwt()
                .jwt(builder -> builder.claim("userId", UUID.randomUUID().toString()).claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
