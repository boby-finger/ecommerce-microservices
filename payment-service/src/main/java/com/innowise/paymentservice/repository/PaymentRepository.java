package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends MongoRepository<Payment, UUID>, PaymentQueryRepository {
    List<Payment> findAllByStatusAndTimestampLessThanEqual(PaymentStatus status,
                                                           Instant timestamp,
                                                           Pageable pageable);
}
