package com.innowise.paymentservice.service;

import com.innowise.paymentservice.client.RandomNumberClient;
import com.innowise.paymentservice.dto.PaymentFilterDto;
import com.innowise.paymentservice.dto.PaymentRequestDto;
import com.innowise.paymentservice.dto.PaymentResponseDto;
import com.innowise.paymentservice.dto.PaymentTotalFilterDto;
import com.innowise.paymentservice.dto.PaymentTotalResponseDto;
import com.innowise.paymentservice.exception.InvalidPaymentFilterException;
import com.innowise.paymentservice.exception.PaymentNotResolvedException;
import com.innowise.paymentservice.exception.RandomNumberUnavailableException;
import com.innowise.paymentservice.mapper.PaymentMapper;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.model.PaymentTotal;
import com.innowise.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-31T23:59:59Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private RandomNumberClient randomNumberClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    @InjectMocks
    private PaymentService paymentService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    void paymentIsPersistedAsPendingBeforeExternalCall() {
        PaymentRequestDto request = request();
        Payment mapped = new Payment();
        when(paymentMapper.toEntity(request)).thenReturn(mapped);
        when(randomNumberClient.nextNumber()).thenReturn(2);
        when(paymentFinalizer.finalizeByRandomNumber(any(Payment.class), anyInt())).thenReturn(mapped);
        when(paymentMapper.toDto(any(Payment.class))).thenReturn(toResponse(mapped));

        paymentService.createPayment(request);

        ArgumentCaptor<Payment> inserted = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).insert(inserted.capture());
        assertThat(inserted.getValue().getId()).isNotNull();
        assertThat(inserted.getValue().getTimestamp()).isNotNull();
        assertThat(inserted.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentFinalizer).finalizeByRandomNumber(mapped, 2);
    }

    @Test
    void unavailableProviderLeavesPaymentPending() {
        PaymentRequestDto request = request();
        Payment mapped = new Payment();
        when(paymentMapper.toEntity(request)).thenReturn(mapped);
        when(randomNumberClient.nextNumber())
                .thenThrow(new RandomNumberUnavailableException("connect timed out"));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(PaymentNotResolvedException.class)
                .hasMessageContaining("PENDING");

        verify(paymentRepository).insert(any(Payment.class));
        verifyNoInteractions(paymentFinalizer);
        assertThat(mapped.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void searchPaymentsPassesFilterCombination() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.search(eq(userId), eq(orderId), eq(PaymentStatus.SUCCESS), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        paymentService.searchPayments(new PaymentFilterDto(userId, orderId, PaymentStatus.SUCCESS), pageable);

        verify(paymentRepository).search(userId, orderId, PaymentStatus.SUCCESS, pageable);
    }

    @Test
    void searchPaymentsRequiresAtLeastOneFilter() {
        assertThatThrownBy(() -> paymentService.searchPayments(
                new PaymentFilterDto(null, null, null), pageable))
                .isInstanceOf(InvalidPaymentFilterException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void getTotalEchoesAppliedFilters() {
        UUID userId = UUID.randomUUID();
        when(paymentRepository.sumAmount(userId, PaymentStatus.SUCCESS, FROM, TO))
                .thenReturn(new PaymentTotal(new BigDecimal("10.35"), 3L));

        PaymentTotalResponseDto total = paymentService.getTotal(userId,
                new PaymentTotalFilterDto(FROM, TO, PaymentStatus.SUCCESS));

        assertThat(total.userId()).isEqualTo(userId);
        assertThat(total.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(total.from()).isEqualTo(FROM);
        assertThat(total.to()).isEqualTo(TO);
        assertThat(total.total()).isEqualByComparingTo("10.35");
        assertThat(total.count()).isEqualTo(3L);
    }

    private static PaymentRequestDto request() {
        return new PaymentRequestDto(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.05"));
    }

    private static PaymentResponseDto toResponse(Payment payment) {
        return new PaymentResponseDto(payment.getId(), payment.getOrderId(), payment.getUserId(),
                payment.getStatus(), payment.getTimestamp(), payment.getPaymentAmount());
    }
}
