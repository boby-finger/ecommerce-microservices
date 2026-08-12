package com.innowise.orderservice.exception;

import java.util.UUID;

public class DuplicateOrderItemException extends RuntimeException {

    public DuplicateOrderItemException(UUID itemId) {
        super("Item " + itemId + " appears more than once in the order, use quantity instead");
    }
}