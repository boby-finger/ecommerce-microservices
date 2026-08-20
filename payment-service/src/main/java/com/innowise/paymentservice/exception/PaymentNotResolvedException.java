package com.innowise.paymentservice.exception;

import java.util.UUID;

public class PaymentNotResolvedException extends RuntimeException {

    private final UUID paymentId;

    public PaymentNotResolvedException(UUID paymentId, Throwable cause) {
        super("Payment " + paymentId + " is saved with status PENDING: the random number provider "
                + "is unavailable, so the final status could not be resolved right now", cause);
        this.paymentId = paymentId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
