package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.model.PaymentTotal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;


public interface PaymentQueryRepository {
    Page<Payment> search(UUID userId, UUID orderId, PaymentStatus status, Pageable pageable);

    PaymentTotal sumAmount(UUID userId, PaymentStatus status, Instant from, Instant to);
}
