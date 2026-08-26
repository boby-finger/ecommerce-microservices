package com.innowise.paymentservice.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentEventPublisher {

    private static final String FAILURE_MARKER = "PAYMENT EVENT NOT PUBLISHED";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final String topic;
    private final Counter publishFailures;

    public PaymentEventPublisher(KafkaTemplate<String, PaymentEvent> kafkaTemplate,
                                 @Value("${kafka.topic.payment-events}") String topic,
                                 MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishFailures = Counter.builder("payment.events.publish.failures")
                .description("Payment events that could not be published to Kafka")
                .register(meterRegistry);
    }

    public void publish(PaymentEvent event) {
        String key = event.orderId().toString();
        log.info("Publishing {} for payment {} (order {}, status {}) to topic {} with key {}",
                event.eventType(), event.paymentId(), event.orderId(), event.status(), topic, key);

        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            reportFailure(event, error);
                            return;
                        }
                        log.info("Published {} for payment {} to {}-{} at offset {}",
                                event.eventType(), event.paymentId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    });
        } catch (RuntimeException e) {
            reportFailure(event, e);
        }
    }

    private void reportFailure(PaymentEvent event, Throwable error) {
        publishFailures.increment();
        log.error("{}: topic={}, key={}, payload={}", FAILURE_MARKER, topic, event.orderId(), event, error);
    }
}
