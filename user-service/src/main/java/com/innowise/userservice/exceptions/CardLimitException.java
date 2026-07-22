package com.innowise.userservice.exceptions;

public class CardLimitException extends RuntimeException {
    public CardLimitException(int max_cards) {
        super("User cannot have more than " + max_cards + " cards");
    }
}
