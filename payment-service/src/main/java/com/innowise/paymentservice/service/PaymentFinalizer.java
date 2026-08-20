package com.innowise.paymentservice.service;

import com.innowise.paymentservice.event.PaymentEventPublisher;
import com.innowise.paymentservice.mapper.PaymentMapper;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFinalizer {
    private static final Set<PaymentStatus> PUBLISHED_STATUSES =
            EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED);

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentEventPublisher paymentEventPublisher;

    public Payment finalizeByRandomNumber(Payment payment, int randomNumber) {
        PaymentStatus status = randomNumber % 2 == 0 ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        log.info("Payment {} resolved by random number {} -> {}", payment.getId(), randomNumber, status);
        return finalizePayment(payment, status);
    }

    public Payment finalizePayment(Payment payment, PaymentStatus status) {
        payment.setStatus(status);
        Payment finalized = paymentRepository.save(payment);
        log.info("Payment {} finalized with status {}", finalized.getId(), status);

        if (PUBLISHED_STATUSES.contains(status)) {
            paymentEventPublisher.publish(paymentMapper.toEvent(finalized, Instant.now()));
        } else {
            log.warn("Payment {} finalized with status {}, no event is published: the status carries "
                    + "no decision order-service could act on", finalized.getId(), status);
        }
        return finalized;
    }
}
