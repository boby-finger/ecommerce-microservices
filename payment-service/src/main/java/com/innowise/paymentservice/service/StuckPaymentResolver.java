package com.innowise.paymentservice.service;

import com.innowise.paymentservice.client.RandomNumberClient;
import com.innowise.paymentservice.exception.RandomNumberUnavailableException;
import com.innowise.paymentservice.model.Payment;
import com.innowise.paymentservice.model.PaymentStatus;
import com.innowise.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class StuckPaymentResolver {

    private final PaymentRepository paymentRepository;
    private final RandomNumberClient randomNumberClient;
    private final PaymentFinalizer paymentFinalizer;
    private final Duration minAge;
    private final Duration giveUpAge;
    private final int batchSize;

    public StuckPaymentResolver(PaymentRepository paymentRepository,
                                RandomNumberClient randomNumberClient,
                                PaymentFinalizer paymentFinalizer,
                                @Value("${payment.resolution.min-age}") Duration minAge,
                                @Value("${payment.resolution.give-up-age}") Duration giveUpAge,
                                @Value("${payment.resolution.batch-size}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.randomNumberClient = randomNumberClient;
        this.paymentFinalizer = paymentFinalizer;
        this.minAge = minAge;
        this.giveUpAge = giveUpAge;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${payment.resolution.interval-ms}")
    public void resolveStuckPayments() {
        Instant now = Instant.now();
        Instant pickUpThreshold = now.minus(minAge);
        Instant giveUpThreshold = now.minus(giveUpAge);

        List<Payment> stuck = paymentRepository.findAllByStatusAndTimestampLessThanEqual(
                PaymentStatus.PENDING,
                pickUpThreshold,
                PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "timestamp")));

        if (stuck.isEmpty()) {
            return;
        }
        log.info("Found {} payment(s) stuck in {} for longer than {}",
                stuck.size(), PaymentStatus.PENDING, minAge);

        for (int i = 0; i < stuck.size(); i++) {
            Payment payment = stuck.get(i);
            try {
                resolveOne(payment, giveUpThreshold);
            } catch (RandomNumberUnavailableException e) {
                log.warn("Random number provider is still unavailable, {} of {} payment(s) stay in {} "
                                + "until the next pass: {}",
                        stuck.size() - i, stuck.size(), PaymentStatus.PENDING, e.getMessage());
                return;
            } catch (RuntimeException e) {
                log.error("Failed to resolve stuck payment {}, continuing with the rest",
                        payment.getId(), e);
            }
        }
    }

    private void resolveOne(Payment payment, Instant giveUpThreshold) {
        if (payment.getTimestamp().isBefore(giveUpThreshold)) {
            log.warn("Payment {} is stuck in {} for longer than {}, marking it as {}",
                    payment.getId(), PaymentStatus.PENDING, giveUpAge, PaymentStatus.UNDETERMINED);
            paymentFinalizer.finalizePayment(payment, PaymentStatus.UNDETERMINED);
            return;
        }
        paymentFinalizer.finalizeByRandomNumber(payment, randomNumberClient.nextNumber());
    }
}
