package com.innowise.orderservice.exception;

import com.innowise.orderservice.model.OrderStatus;

import java.util.UUID;

public class OrderNotModifiableException extends RuntimeException {

    public OrderNotModifiableException(UUID id, OrderStatus status) {
        super("Order " + id + " cannot be modified while it is " + status);
    }
}