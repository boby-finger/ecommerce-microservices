package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.IntegrationTestBase;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.model.PaymentTotal;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentQueryRepositoryImplTest extends IntegrationTestBase {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-31T23:59:59Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    @Test
    void amountsAreStoredAsDecimal128() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.05", Instant.parse("2026-01-10T12:00:00Z"));

        Document raw = mongoTemplate.getCollection("payments").find().first();

        assertThat(raw).isNotNull();
        assertThat(raw.get("payment_amount")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) raw.get("payment_amount")).bigDecimalValue())
                .isEqualByComparingTo("10.05");
    }

    @Test
    void sumIsExactForDecimalValues() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "0.10", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "0.20", Instant.parse("2026-01-11T12:00:00Z"));

        PaymentTotal total = paymentRepository.sumAmount(userId, null, FROM, TO);

        assertThat(total.amount()).isEqualByComparingTo("0.30");
        assertThat(total.amount().subtract(new BigDecimal("0.30")).signum()).isZero();
        assertThat(total.count()).isEqualTo(2L);
    }

    @Test
    void sumKeepsPrecisionForLargeValues() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "99999999999999.99",
                Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "0.01",
                Instant.parse("2026-01-11T12:00:00Z"));

        PaymentTotal total = paymentRepository.sumAmount(userId, null, FROM, TO);

        assertThat(total.amount()).isEqualByComparingTo("100000000000000.00");
        assertThat(total.amount().toPlainString()).doesNotContain("E");
    }

    @Test
    void sumRespectsFilters() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "10.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.FAILED, "5.00", Instant.parse("2026-01-11T12:00:00Z"));
        save(otherUserId, UUID.randomUUID(), PaymentStatus.SUCCESS, "7.00", Instant.parse("2026-01-12T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "100.00", Instant.parse("2026-02-01T12:00:00Z"));

        assertThat(paymentRepository.sumAmount(userId, null, FROM, TO).amount())
                .isEqualByComparingTo("15.00");
        assertThat(paymentRepository.sumAmount(userId, PaymentStatus.SUCCESS, FROM, TO).amount())
                .isEqualByComparingTo("10.00");
        assertThat(paymentRepository.sumAmount(null, PaymentStatus.SUCCESS, FROM, TO).amount())
                .isEqualByComparingTo("17.00");
        assertThat(paymentRepository.sumAmount(null, null, FROM, TO).count()).isEqualTo(3L);
    }

    @Test
    void rangeBoundsAreInclusive() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "1.00", FROM);
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "2.00", TO);

        assertThat(paymentRepository.sumAmount(userId, null, FROM, TO).amount())
                .isEqualByComparingTo("3.00");
    }

    @Test
    void emptyMatchReturnsZero() {
        PaymentTotal total = paymentRepository.sumAmount(userId, null, FROM, TO);

        assertThat(total.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(total.count()).isZero();
    }

    @Test
    void searchCombinesConditions() {
        UUID orderId = UUID.randomUUID();
        save(userId, orderId, PaymentStatus.SUCCESS, "1.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, orderId, PaymentStatus.FAILED, "2.00", Instant.parse("2026-01-11T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "3.00", Instant.parse("2026-01-12T12:00:00Z"));
        save(otherUserId, orderId, PaymentStatus.SUCCESS, "4.00", Instant.parse("2026-01-13T12:00:00Z"));

        PageRequest page = PageRequest.of(0, 20);

        assertThat(paymentRepository.search(userId, null, null, page).getTotalElements()).isEqualTo(3);
        assertThat(paymentRepository.search(null, orderId, null, page).getTotalElements()).isEqualTo(3);
        assertThat(paymentRepository.search(null, null, PaymentStatus.SUCCESS, page).getTotalElements())
                .isEqualTo(3);
        assertThat(paymentRepository.search(userId, orderId, null, page).getTotalElements()).isEqualTo(2);
        assertThat(paymentRepository.search(userId, orderId, PaymentStatus.SUCCESS, page).getTotalElements())
                .isEqualTo(1);
        assertThat(paymentRepository.search(userId, null, null, page).getContent())
                .allMatch(payment -> payment.getUserId().equals(userId));
    }

    @Test
    void searchIsPagedAndSorted() {
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "1.00", Instant.parse("2026-01-10T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "2.00", Instant.parse("2026-01-11T12:00:00Z"));
        save(userId, UUID.randomUUID(), PaymentStatus.SUCCESS, "3.00", Instant.parse("2026-01-12T12:00:00Z"));

        PageRequest firstPage = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Payment> page = paymentRepository.search(userId, null, null, firstPage);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getPaymentAmount()).isEqualByComparingTo("3.00");
        assertThat(page.getContent().get(1).getPaymentAmount()).isEqualByComparingTo("2.00");
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
}
