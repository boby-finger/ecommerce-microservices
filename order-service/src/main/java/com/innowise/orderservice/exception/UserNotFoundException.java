package com.innowise.orderservice.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}