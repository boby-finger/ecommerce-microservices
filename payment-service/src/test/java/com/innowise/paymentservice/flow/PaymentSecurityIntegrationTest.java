package com.innowise.paymentservice.flow;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.paymentservice.IntegrationTestBase;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentSecurityIntegrationTest extends IntegrationTestBase {

    private static final String FROM = "2026-01-01T00:00:00Z";
    private static final String TO = "2026-01-31T23:59:59Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID ownerId;
    private UUID strangerId;
    private UUID strangerOrderId;
    private UUID strangerPaymentId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        ownerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        strangerOrderId = UUID.randomUUID();
        strangerPaymentId = save(strangerId, strangerOrderId, PaymentStatus.SUCCESS, "999.00");
    }

    @Test
    void anonymousIsRejectedExceptHealth() throws Exception {
        mockMvc.perform(get("/api/v1/payments").param("status", "SUCCESS"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void userCreatesOnlyOwnPayment() throws Exception {
        stubRandomNumber();

        mockMvc.perform(post("/api/v1/payments")
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(ownerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/payments")
                        .with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson(strangerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void userCannotSeeAnotherUsersPaymentEvenWithForgedUserId() throws Exception {
        save(ownerId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00");

        mockMvc.perform(get("/api/v1/payments")
                        .param("userId", strangerId.toString())
                        .with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.content[0].id").value(org.hamcrest.Matchers.not(
                        strangerPaymentId.toString())));

        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", strangerOrderId.toString())
                        .with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", strangerOrderId.toString())
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(strangerId.toString()));
    }

    @Test
    void userMayCombineOwnScopeWithOtherFilters() throws Exception {
        UUID orderId = UUID.randomUUID();
        save(ownerId, orderId, PaymentStatus.SUCCESS, "10.00");
        save(ownerId, orderId, PaymentStatus.FAILED, "20.00");
        save(ownerId, UUID.randomUUID(), PaymentStatus.SUCCESS, "30.00");

        mockMvc.perform(get("/api/v1/payments").param("status", "SUCCESS").with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/payments").param("orderId", orderId.toString()).with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/payments")
                        .param("orderId", orderId.toString())
                        .param("status", "SUCCESS")
                        .with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/payments").with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void adminIsNotScoped() throws Exception {
        save(ownerId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00");

        mockMvc.perform(get("/api/v1/payments").param("userId", strangerId.toString()).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(strangerId.toString()));

        mockMvc.perform(get("/api/v1/payments").param("status", "SUCCESS").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void totalForAllUsersIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/payments/total").param("from", FROM).param("to", TO).with(asUser(ownerId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/payments/total").param("from", FROM).param("to", TO).with(asAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void totalForCurrentUserIgnoresQueryParam() throws Exception {
        save(ownerId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00");

        mockMvc.perform(get("/api/v1/payments/total/me")
                        .param("from", FROM).param("to", TO)
                        .param("userId", strangerId.toString())
                        .with(asUser(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value(10.00));
    }

    @Test
    void tokenWithoutUserIdClaimIsRejected() throws Exception {
        RequestPostProcessor withoutClaim = jwt()
                .jwt(builder -> builder.claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        mockMvc.perform(get("/api/v1/payments/total/me")
                        .param("from", FROM).param("to", TO).with(withoutClaim))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/payments").param("status", "SUCCESS").with(withoutClaim))
                .andExpect(status().isForbidden());
    }

    private void stubRandomNumber() {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("42\n")));
    }

    private UUID save(UUID user, UUID order, PaymentStatus status, String amount) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order);
        payment.setUserId(user);
        payment.setStatus(status);
        payment.setPaymentAmount(new BigDecimal(amount));
        payment.setTimestamp(Instant.parse("2026-01-10T12:00:00Z"));
        return paymentRepository.insert(payment).getId();
    }

    private String paymentJson(UUID user) {
        return """
                {
                  "orderId": "%s",
                  "userId": "%s",
                  "paymentAmount": 10.00
                }
                """.formatted(UUID.randomUUID(), user);
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
