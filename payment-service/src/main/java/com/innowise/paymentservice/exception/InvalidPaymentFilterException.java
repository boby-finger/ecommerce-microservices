package com.innowise.paymentservice.exception;

public class InvalidPaymentFilterException extends RuntimeException {

    public InvalidPaymentFilterException() {
        super("At least one filter of userId, orderId or status must be provided");
    }
}
