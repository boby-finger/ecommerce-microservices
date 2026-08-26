package com.innowise.paymentservice.event;

import com.innowise.paymentservice.model.PaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    private static final String TOPIC = "payment-events";
    private static final String FAILURE_METRIC = "payment.events.publish.failures";

    @Mock
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    private MeterRegistry meterRegistry;
    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new PaymentEventPublisher(kafkaTemplate, TOPIC, meterRegistry);
    }

    @Test
    void publishesWithOrderIdAsKey() {
        PaymentEvent event = event();
        when(kafkaTemplate.send(eq(TOPIC), eq(event.orderId().toString()), any(PaymentEvent.class)))
                .thenReturn(new CompletableFuture<>());

        publisher.publish(event);

        verify(kafkaTemplate).send(TOPIC, event.orderId().toString(), event);
        assertThat(failureCount()).isZero();
    }

    @Test
    void asyncFailureIsCountedNotThrown() {
        PaymentEvent event = event();
        CompletableFuture<SendResult<String, PaymentEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TimeoutException(
                "Topic payment-events not present in metadata after 500 ms."));
        when(kafkaTemplate.send(any(), any(), any(PaymentEvent.class))).thenReturn(failed);

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(failureCount()).isEqualTo(1.0);
    }

    @Test
    void syncFailureIsCountedNotThrown() {
        PaymentEvent event = event();
        when(kafkaTemplate.send(any(), any(), any(PaymentEvent.class)))
                .thenThrow(new KafkaException("Failed to send", new TimeoutException("no metadata")));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(failureCount()).isEqualTo(1.0);
    }

    private double failureCount() {
        return meterRegistry.counter(FAILURE_METRIC).count();
    }

    private static PaymentEvent event() {
        return new PaymentEvent(
                PaymentEventType.CREATE_PAYMENT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.SUCCESS,
                new BigDecimal("149.99"),
                Instant.parse("2026-01-10T12:00:00Z"),
                Instant.parse("2026-01-10T12:00:01Z"));
    }
}
