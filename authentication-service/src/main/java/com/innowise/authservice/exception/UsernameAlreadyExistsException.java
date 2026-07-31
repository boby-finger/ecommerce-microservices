package com.innowise.authservice.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("Username is already taken: " + username + ", try another one");
    }
}
