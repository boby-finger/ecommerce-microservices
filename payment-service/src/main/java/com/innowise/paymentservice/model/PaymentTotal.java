package com.innowise.paymentservice.model;

import java.math.BigDecimal;

public record PaymentTotal(
        BigDecimal amount,
        long count
) {
    public static final PaymentTotal EMPTY = new PaymentTotal(BigDecimal.ZERO, 0L);
}
