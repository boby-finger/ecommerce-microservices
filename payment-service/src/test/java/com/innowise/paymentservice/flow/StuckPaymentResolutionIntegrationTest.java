package com.innowise.paymentservice.flow;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.paymentservice.IntegrationTestBase;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.innowise.paymentservice.service.StuckPaymentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StuckPaymentResolutionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private StuckPaymentResolver stuckPaymentResolver;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    void schedulingIsDisabledInTests() {
        assertThat(applicationContext.containsBean("schedulingConfig")).isFalse();
        assertThat(applicationContext.containsBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
        assertThat(stuckPaymentResolver).isNotNull();
    }

    @Test
    void stuckPendingIsFinalized() {
        stubRandomNumber("42");
        UUID stuckId = save(PaymentStatus.PENDING, Instant.now().minus(10, ChronoUnit.MINUTES));

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(stuckId)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void stuckPendingResolvesToFailedOnOddNumber() {
        stubRandomNumber("43");
        UUID stuckId = save(PaymentStatus.PENDING, Instant.now().minus(10, ChronoUnit.MINUTES));

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(stuckId)).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void unavailableProviderLeavesPaymentPending() {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse().withStatus(500)));
        UUID stuckId = save(PaymentStatus.PENDING, Instant.now().minus(10, ChronoUnit.MINUTES));

        stuckPaymentResolver.resolveStuckPayments();
        assertThat(status(stuckId)).isEqualTo(PaymentStatus.PENDING);

        RANDOM_NUMBER_API.resetAll();
        stubRandomNumber("8");
        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(stuckId)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void freshPaymentIsNotPickedUp() {
        stubRandomNumber("42");
        UUID freshId = save(PaymentStatus.PENDING, Instant.now());

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(freshId)).isEqualTo(PaymentStatus.PENDING);
        RANDOM_NUMBER_API.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void tooOldPaymentBecomesUndetermined() {
        stubRandomNumber("42");
        UUID ancientId = save(PaymentStatus.PENDING, Instant.now().minus(3, ChronoUnit.HOURS));

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(ancientId)).isEqualTo(PaymentStatus.UNDETERMINED);
        RANDOM_NUMBER_API.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
        stuckPaymentResolver.resolveStuckPayments();
        assertThat(status(ancientId)).isEqualTo(PaymentStatus.UNDETERMINED);
    }

    @Test
    void finalizedPaymentsAreUntouched() {
        stubRandomNumber("42");
        UUID successId = save(PaymentStatus.SUCCESS, Instant.now().minus(10, ChronoUnit.MINUTES));
        UUID failedId = save(PaymentStatus.FAILED, Instant.now().minus(10, ChronoUnit.MINUTES));

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(successId)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(status(failedId)).isEqualTo(PaymentStatus.FAILED);
        RANDOM_NUMBER_API.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void wholeBatchIsResolvedInOnePass() {
        stubRandomNumber("2");
        UUID first = save(PaymentStatus.PENDING, Instant.now().minus(12, ChronoUnit.MINUTES));
        UUID second = save(PaymentStatus.PENDING, Instant.now().minus(11, ChronoUnit.MINUTES));
        UUID third = save(PaymentStatus.PENDING, Instant.now().minus(10, ChronoUnit.MINUTES));

        stuckPaymentResolver.resolveStuckPayments();

        assertThat(status(first)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(status(second)).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(status(third)).isEqualTo(PaymentStatus.SUCCESS);
    }

    private void stubRandomNumber(String body) {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(body + "\n")));
    }

    private UUID save(PaymentStatus status, Instant timestamp) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setStatus(status);
        payment.setPaymentAmount(new BigDecimal("10.00"));
        payment.setTimestamp(timestamp);
        return paymentRepository.insert(payment).getId();
    }

    private PaymentStatus status(UUID id) {
        return paymentRepository.findById(id).orElseThrow().getStatus();
    }
}
