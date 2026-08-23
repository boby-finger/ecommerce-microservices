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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final RandomNumberClient randomNumberClient;
    private final PaymentFinalizer paymentFinalizer;

    public PaymentResponseDto createPayment(PaymentRequestDto request) {
        Payment payment = paymentMapper.toEntity(request);
        payment.setId(UUID.randomUUID());
        payment.setTimestamp(Instant.now());
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.insert(payment);
        log.info("Payment {} created with status {}", payment.getId(), PaymentStatus.PENDING);

        int randomNumber;
        try {
            randomNumber = randomNumberClient.nextNumber();
        } catch (RandomNumberUnavailableException e) {
            log.warn("Payment {} stays in {}: {}", payment.getId(), PaymentStatus.PENDING, e.getMessage());
            throw new PaymentNotResolvedException(payment.getId(), e);
        }

        return paymentMapper.toDto(paymentFinalizer.finalizeByRandomNumber(payment, randomNumber));
    }

    public Page<PaymentResponseDto> searchPayments(PaymentFilterDto filter, Pageable pageable) {
        if (filter.isEmpty()) {
            throw new InvalidPaymentFilterException();
        }
        return paymentRepository.search(filter.userId(), filter.orderId(), filter.status(), pageable)
                .map(paymentMapper::toDto);
    }

    public PaymentTotalResponseDto getTotal(UUID userId, PaymentTotalFilterDto filter) {
        PaymentTotal total = paymentRepository.sumAmount(
                userId, filter.status(), filter.from(), filter.to());
        return new PaymentTotalResponseDto(userId, filter.status(), filter.from(), filter.to(),
                total.amount(), total.count());
    }
}
