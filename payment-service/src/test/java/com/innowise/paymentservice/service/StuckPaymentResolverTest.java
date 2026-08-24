package com.innowise.paymentservice.service;

import com.innowise.paymentservice.client.RandomNumberClient;
import com.innowise.paymentservice.exception.RandomNumberUnavailableException;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StuckPaymentResolverTest {

    private static final Duration MIN_AGE = Duration.ofMinutes(1);
    private static final Duration GIVE_UP_AGE = Duration.ofHours(2);
    private static final int BATCH_SIZE = 100;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RandomNumberClient randomNumberClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    private StuckPaymentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StuckPaymentResolver(paymentRepository, randomNumberClient, paymentFinalizer,
                MIN_AGE, GIVE_UP_AGE, BATCH_SIZE);
    }

    @Test
    void selectsOnlyPendingOlderThanMinAge() {
        Instant before = Instant.now();
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(
                any(), any(), any())).thenReturn(List.of());

        resolver.resolveStuckPayments();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findAllByStatusAndTimestampLessThanEqual(
                eq(PaymentStatus.PENDING), threshold.capture(), pageable.capture());

        assertThat(threshold.getValue())
                .isBetween(before.minus(MIN_AGE), after.minus(MIN_AGE));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(BATCH_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("timestamp")).isNotNull();
        verifyNoInteractions(randomNumberClient, paymentFinalizer);
    }

    @Test
    void stuckPaymentIsFinalized() {
        Payment stuck = payment(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(stuck));
        when(randomNumberClient.nextNumber()).thenReturn(4);

        resolver.resolveStuckPayments();

        verify(paymentFinalizer).finalizeByRandomNumber(stuck, 4);
    }

    @Test
    void unavailableProviderLeavesPaymentsPending() {
        Payment first = payment(Instant.now().minus(10, ChronoUnit.MINUTES));
        Payment second = payment(Instant.now().minus(9, ChronoUnit.MINUTES));
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(first, second));
        when(randomNumberClient.nextNumber())
                .thenThrow(new RandomNumberUnavailableException("connect timed out"));

        resolver.resolveStuckPayments();

        verify(randomNumberClient, times(1)).nextNumber();
        verify(paymentFinalizer, never()).finalizeByRandomNumber(any(), anyInt());
        verify(paymentFinalizer, never()).finalizePayment(any(), any());
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(second.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void tooOldPaymentBecomesUndetermined() {
        Payment ancient = payment(Instant.now().minus(3, ChronoUnit.HOURS));
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(ancient));

        resolver.resolveStuckPayments();

        verify(paymentFinalizer).finalizePayment(ancient, PaymentStatus.UNDETERMINED);
        verifyNoInteractions(randomNumberClient);
    }

    @Test
    void failureOnOnePaymentDoesNotStopThePass() {
        Payment broken = payment(Instant.now().minus(12, ChronoUnit.MINUTES));
        Payment second = payment(Instant.now().minus(11, ChronoUnit.MINUTES));
        Payment third = payment(Instant.now().minus(10, ChronoUnit.MINUTES));
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(broken, second, third));
        when(randomNumberClient.nextNumber()).thenReturn(2);
        doThrow(new DataAccessResourceFailureException("write failed"))
                .when(paymentFinalizer).finalizeByRandomNumber(eq(broken), anyInt());

        resolver.resolveStuckPayments();

        verify(paymentFinalizer).finalizeByRandomNumber(broken, 2);
        verify(paymentFinalizer).finalizeByRandomNumber(second, 2);
        verify(paymentFinalizer).finalizeByRandomNumber(third, 2);
    }

    @Test
    void emptyBatchDoesNothing() {
        when(paymentRepository.findAllByStatusAndTimestampLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        resolver.resolveStuckPayments();

        verifyNoInteractions(randomNumberClient, paymentFinalizer);
    }

    private static Payment payment(Instant timestamp) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTimestamp(timestamp);
        return payment;
    }
}
