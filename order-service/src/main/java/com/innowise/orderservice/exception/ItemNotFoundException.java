package com.innowise.orderservice.exception;

import java.util.Collection;
import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(Collection<UUID> ids) {
        super("Items not found: " + ids);
    }
}