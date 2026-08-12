package com.innowise.orderservice.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException() {
        super("User service is unavailable, the order cannot be placed right now");
    }
}