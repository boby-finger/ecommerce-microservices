package com.innowise.userservice.exceptions;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("User with id " + id + " not found");
    }
    public UserNotFoundException(String email) { super("User not found with email: " + email); }
}
