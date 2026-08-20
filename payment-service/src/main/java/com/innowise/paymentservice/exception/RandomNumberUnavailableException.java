package com.innowise.paymentservice.exception;

public class RandomNumberUnavailableException extends RuntimeException {

    public RandomNumberUnavailableException(String reason, Throwable cause) {
        super("Random number provider is unavailable: " + reason, cause);
    }

    public RandomNumberUnavailableException(String reason) {
        super("Random number provider is unavailable: " + reason);
    }
}
