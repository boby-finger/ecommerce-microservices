package com.innowise.paymentservice.flow;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.innowise.paymentservice.IntegrationTestBase;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.innowise.paymentservice.service.PaymentFinalizer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentEventIntegrationTest extends IntegrationTestBase {

    private static final String TOPIC = "payment-events";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.auto-create", () -> true);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentFinalizer paymentFinalizer;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${kafka.topic.partitions}")
    private int partitions;

    private static Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        consumer().poll(Duration.ofMillis(200));
    }

    @AfterAll
    static void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void createPaymentPublishesEvent() throws Exception {
        stubRandomNumber("42");
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String created = mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "userId": "%s",
                                  "paymentAmount": 149.99
                                }
                                """.formatted(orderId, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(created).get("id").asString();

        ConsumerRecord<String, String> record = awaitSingleRecord();

        assertThat(record.key()).isEqualTo(orderId.toString());

        JsonNode payload = objectMapper.readTree(record.value());
        assertThat(payload.get("eventType").asString()).isEqualTo("CREATE_PAYMENT");
        assertThat(payload.get("paymentId").asString()).isEqualTo(paymentId);
        assertThat(payload.get("orderId").asString()).isEqualTo(orderId.toString());
        assertThat(payload.get("userId").asString()).isEqualTo(userId.toString());
        assertThat(payload.get("status").asString()).isEqualTo("SUCCESS");
        assertThat(new BigDecimal(payload.get("paymentAmount").asString()))
                .isEqualByComparingTo("149.99");
        assertThat(payload.has("timestamp")).isTrue();
        assertThat(payload.has("occurredAt")).isTrue();
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    void failedPaymentIsPublished() throws Exception {
        stubRandomNumber("43");

        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "userId": "%s",
                                  "paymentAmount": 10.00
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        JsonNode payload = objectMapper.readTree(awaitSingleRecord().value());
        assertThat(payload.get("status").asString()).isEqualTo("FAILED");
    }

    @Test
    void undeterminedIsNotPublished() {
        Payment stuck = new Payment();
        stuck.setId(UUID.randomUUID());
        stuck.setOrderId(UUID.randomUUID());
        stuck.setUserId(UUID.randomUUID());
        stuck.setStatus(PaymentStatus.PENDING);
        stuck.setPaymentAmount(new BigDecimal("10.00"));
        stuck.setTimestamp(Instant.now().minusSeconds(3600 * 3));
        paymentRepository.insert(stuck);

        paymentFinalizer.finalizePayment(stuck, PaymentStatus.UNDETERMINED);

        assertThat(pollRecords(Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void keyDeterminesPartition() throws Exception {
        stubRandomNumber("42");
        UUID sharedOrderId = UUID.randomUUID();

        createPayment(sharedOrderId);
        createPayment(sharedOrderId);
        createPayment(UUID.randomUUID());

        List<ConsumerRecord<String, String>> records = awaitRecords(3);
        Map<String, List<Integer>> partitionsByKey = new java.util.HashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            partitionsByKey.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(record.partition());
        }

        assertThat(partitions).isGreaterThan(1);
        assertThat(partitionsByKey.get(sharedOrderId.toString()))
                .hasSize(2)
                .containsOnly(partitionsByKey.get(sharedOrderId.toString()).get(0));
    }

    private void createPayment(UUID orderId) throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": "%s",
                                  "userId": "%s",
                                  "paymentAmount": 5.00
                                }
                                """.formatted(orderId, UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    private ConsumerRecord<String, String> awaitSingleRecord() {
        List<ConsumerRecord<String, String>> records = awaitRecords(1);
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private List<ConsumerRecord<String, String>> awaitRecords(int expected) {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        Instant deadline = Instant.now().plusSeconds(20);
        while (collected.size() < expected && Instant.now().isBefore(deadline)) {
            collected.addAll(pollRecords(Duration.ofMillis(500)));
        }
        assertThat(collected).hasSize(expected);
        return collected;
    }

    private List<ConsumerRecord<String, String>> pollRecords(Duration timeout) {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        ConsumerRecords<String, String> polled = consumer().poll(timeout);
        polled.records(TOPIC).forEach(collected::add);
        return collected;
    }

    private static Consumer<String, String> consumer() {
        if (consumer == null) {
            consumer = new KafkaConsumer<>(Map.of(
                    "bootstrap.servers", KAFKA.getBootstrapServers(),
                    "group.id", "payment-events-test-" + UUID.randomUUID(),
                    "auto.offset.reset", "earliest",
                    "enable.auto.commit", "true"
            ), new StringDeserializer(), new StringDeserializer());
            consumer.subscribe(List.of(TOPIC));
        }
        return consumer;
    }

    private void stubRandomNumber(String body) {
        RANDOM_NUMBER_API.stubFor(WireMock.get(WireMock.urlPathEqualTo(RANDOM_NUMBER_PATH))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(body + "\n")));
    }

    private RequestPostProcessor asAdmin() {
        return jwt()
                .jwt(builder -> builder.claim("userId", UUID.randomUUID().toString()).claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
