package com.innowise.userservice.exceptions;

import java.util.UUID;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException(UUID id) {
        super("Card with " + id + " not found");
    }
}
