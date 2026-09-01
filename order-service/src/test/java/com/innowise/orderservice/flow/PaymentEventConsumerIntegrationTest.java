package com.innowise.orderservice.flow;

import com.innowise.orderservice.IntegrationTestBase;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kafka поднимается только для этого класса: остальным тестам order-service брокер
 * не нужен, и общий {@link IntegrationTestBase} его не тянет.
 */
class PaymentEventConsumerIntegrationTest extends IntegrationTestBase {

    private static final String TOPIC = "payment-events";
    private static final String DLT = TOPIC + "-dlt";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.auto-startup", () -> true);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    private static Producer<String, String> producer;

    @BeforeEach
    void cleanOrders() {
        orderRepository.deleteAll();
        itemRepository.deleteAll();
    }

    @AfterAll
    static void closeClients() {
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }

    @Test
    @DisplayName("SUCCESS переводит заказ в PAID")
    void successMovesOrderToPaid() {
        UUID orderId = saveOrder(OrderStatus.CREATED);

        send(orderId, event(orderId, "SUCCESS"));

        awaitStatus(orderId, OrderStatus.PAID);
    }

    @Test
    @DisplayName("FAILED переводит заказ в PAYMENT_FAILED")
    void failedMovesOrderToPaymentFailed() {
        UUID orderId = saveOrder(OrderStatus.CREATED);

        send(orderId, event(orderId, "FAILED"));

        awaitStatus(orderId, OrderStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("после неудачной оплаты успешная всё равно переводит заказ в PAID")
    void successAfterFailureStillPays() {
        UUID orderId = saveOrder(OrderStatus.CREATED);

        send(orderId, event(orderId, "FAILED"));
        awaitStatus(orderId, OrderStatus.PAYMENT_FAILED);

        send(orderId, event(orderId, "SUCCESS"));
        awaitStatus(orderId, OrderStatus.PAID);
    }

    @Test
    @DisplayName("повторная доставка того же события ничего не ломает")
    void duplicateDeliveryIsIdempotent() {
        UUID orderId = saveOrder(OrderStatus.CREATED);
        String payload = event(orderId, "SUCCESS");

        send(orderId, payload);
        awaitStatus(orderId, OrderStatus.PAID);
        Instant afterFirst = orderRepository.findById(orderId).orElseThrow().getUpdatedAt();

        send(orderId, payload);
        send(orderId, payload);

        // статус остаётся PAID; ждём, пока обе копии точно будут прочитаны
        await().atMost(Duration.ofSeconds(15)).pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo(OrderStatus.PAID));
        assertThat(orderRepository.findById(orderId).orElseThrow().getUpdatedAt())
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("оплаченный заказ не откатывается назад более старым FAILED")
    void paidOrderIsNotDowngraded() {
        UUID orderId = saveOrder(OrderStatus.CREATED);

        send(orderId, event(orderId, "SUCCESS"));
        awaitStatus(orderId, OrderStatus.PAID);

        send(orderId, event(orderId, "FAILED"));

        await().atMost(Duration.ofSeconds(15)).pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo(OrderStatus.PAID));
    }

    @Test
    @DisplayName("событие с неизвестным orderId не роняет консьюмера")
    void unknownOrderDoesNotBreakConsumer() {
        UUID unknownOrderId = UUID.randomUUID();
        UUID knownOrderId = saveOrder(OrderStatus.CREATED);

        send(unknownOrderId, event(unknownOrderId, "SUCCESS"));
        send(knownOrderId, event(knownOrderId, "SUCCESS"));

        // следующее валидное событие обработано -- значит консьюмер жив и не застрял
        awaitStatus(knownOrderId, OrderStatus.PAID);
    }

    @Test
    @DisplayName("UNDETERMINED статус заказа не меняет, консьюмер продолжает работать")
    void undeterminedLeavesOrderUnchanged() {
        UUID undeterminedOrderId = saveOrder(OrderStatus.CREATED);
        UUID nextOrderId = saveOrder(OrderStatus.CREATED);

        send(undeterminedOrderId, event(undeterminedOrderId, "UNDETERMINED"));
        send(nextOrderId, event(nextOrderId, "SUCCESS"));

        awaitStatus(nextOrderId, OrderStatus.PAID);
        assertThat(status(undeterminedOrderId)).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("нечитаемое сообщение уезжает в DLT, очередь не встаёт")
    void unparseableMessageGoesToDeadLetterTopic() {
        UUID orderId = saveOrder(OrderStatus.CREATED);

        send(UUID.randomUUID(), "{ this is not a payment event }");
        send(orderId, event(orderId, "SUCCESS"));

        // валидное сообщение обработано -- значит битое не встало пробкой в партиции
        awaitStatus(orderId, OrderStatus.PAID);

        // консьюмер создаётся только сейчас: топик .DLT появляется в момент первой
        // отправки, а подписка на несуществующий топик обновляла бы метаданные лишь
        // раз в metadata.max.age.ms
        List<ConsumerRecord<String, String>> dead = new ArrayList<>();
        try (Consumer<String, String> consumer = dltConsumer()) {
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                polled.records(DLT).forEach(dead::add);
                assertThat(dead).isNotEmpty();
            });
        }
        assertThat(dead.get(0).headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
    }

    @Test
    @DisplayName("лишнее поле в сообщении не ломает разбор")
    void unknownFieldIsIgnored() {
        UUID orderId = saveOrder(OrderStatus.CREATED);
        String payload = """
                {
                  "eventType": "CREATE_PAYMENT",
                  "paymentId": "%s",
                  "orderId": "%s",
                  "userId": "%s",
                  "status": "SUCCESS",
                  "paymentAmount": 149.99,
                  "timestamp": "2026-01-10T12:00:00Z",
                  "occurredAt": "2026-01-10T12:00:01Z",
                  "fieldAddedByProducerLater": "whatever"
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID());

        send(orderId, payload);

        awaitStatus(orderId, OrderStatus.PAID);
    }

    private void awaitStatus(UUID orderId, OrderStatus expected) {
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(status(orderId)).isEqualTo(expected));
    }

    private OrderStatus status(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private static String event(UUID orderId, String status) {
        return """
                {
                  "eventType": "CREATE_PAYMENT",
                  "paymentId": "%s",
                  "orderId": "%s",
                  "userId": "%s",
                  "status": "%s",
                  "paymentAmount": 149.99,
                  "timestamp": "2026-01-10T12:00:00Z",
                  "occurredAt": "2026-01-10T12:00:01Z"
                }
                """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID(), status);
    }

    private void send(UUID key, String payload) {
        try {
            producer().send(new ProducerRecord<>(TOPIC, key.toString(), payload)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID saveOrder(OrderStatus status) {
        Item item = new Item();
        item.setName("Item " + UUID.randomUUID());
        item.setPrice(new BigDecimal("149.99"));
        Item savedItem = itemRepository.save(item);

        Order order = new Order();
        order.setUserId(UUID.randomUUID());
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("149.99"));
        order.setDeleted(false);

        OrderItem orderItem = new OrderItem();
        orderItem.setItem(savedItem);
        orderItem.setQuantity(1);
        order.addItem(orderItem);

        return orderRepository.save(order).getId();
    }

    private static Producer<String, String> producer() {
        if (producer == null) {
            producer = new KafkaProducer<>(Map.of(
                    "bootstrap.servers", KAFKA.getBootstrapServers(),
                    "acks", "all"
            ), new StringSerializer(), new StringSerializer());
        }
        return producer;
    }

    private static Consumer<String, String> dltConsumer() {
        Consumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "group.id", "dlt-test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "enable.auto.commit", "true",
                "metadata.max.age.ms", "1000"
        ), new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(DLT));
        return consumer;
    }
}
