package com.innowise.paymentservice.service;

import com.innowise.paymentservice.event.PaymentEvent;
import com.innowise.paymentservice.event.PaymentEventPublisher;
import com.innowise.paymentservice.event.PaymentEventType;
import com.innowise.paymentservice.mapper.PaymentMapper;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFinalizerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentFinalizer paymentFinalizer;

    @ParameterizedTest(name = "чётное {0} -> SUCCESS")
    @ValueSource(ints = {0, 2, 42, -4, 1000000})
    void evenNumberResolvesToSuccess(int number) {
        assertThat(finalizeWith(number)).isEqualTo(PaymentStatus.SUCCESS);
    }

    @ParameterizedTest(name = "нечётное {0} -> FAILED")
    @ValueSource(ints = {1, 3, 41, -5, 999999})
    void oddNumberResolvesToFailed(int number) {
        assertThat(finalizeWith(number)).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void finalizePaymentPersistsStatusAndPublishes() {
        Payment payment = payment();
        stubSave();
        PaymentEvent event = event(payment, PaymentStatus.SUCCESS);
        when(paymentMapper.toEvent(any(Payment.class), any(Instant.class))).thenReturn(event);

        Payment finalized = paymentFinalizer.finalizePayment(payment, PaymentStatus.SUCCESS);

        assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository).save(payment);
        verify(paymentEventPublisher).publish(event);
    }

    @Test
    void undeterminedIsPersistedButNotPublished() {
        Payment payment = payment();
        stubSave();

        Payment finalized = paymentFinalizer.finalizePayment(payment, PaymentStatus.UNDETERMINED);

        assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.UNDETERMINED);
        verify(paymentRepository).save(payment);
        verifyNoInteractions(paymentEventPublisher, paymentMapper);
    }

    @Test
    void eventIsBuiltFromPersistedPayment() {
        Payment payment = payment();
        stubSave();
        when(paymentMapper.toEvent(any(Payment.class), any(Instant.class)))
                .thenReturn(event(payment, PaymentStatus.FAILED));

        paymentFinalizer.finalizePayment(payment, PaymentStatus.FAILED);

        ArgumentCaptor<Payment> source = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).toEvent(source.capture(), any(Instant.class));
        assertThat(source.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private PaymentStatus finalizeWith(int number) {
        Payment payment = payment();
        stubSave();
        lenient().when(paymentMapper.toEvent(any(Payment.class), any(Instant.class)))
                .thenReturn(event(payment, PaymentStatus.SUCCESS));

        return paymentFinalizer.finalizeByRandomNumber(payment, number).getStatus();
    }

    private void stubSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static Payment payment() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(UUID.randomUUID());
        payment.setUserId(UUID.randomUUID());
        payment.setPaymentAmount(new BigDecimal("10.00"));
        payment.setTimestamp(Instant.parse("2026-01-10T12:00:00Z"));
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }

    private static PaymentEvent event(Payment payment, PaymentStatus status) {
        return new PaymentEvent(PaymentEventType.CREATE_PAYMENT, payment.getId(), payment.getOrderId(),
                payment.getUserId(), status, payment.getPaymentAmount(), payment.getTimestamp(),
                Instant.parse("2026-01-10T12:00:01Z"));
    }
}
