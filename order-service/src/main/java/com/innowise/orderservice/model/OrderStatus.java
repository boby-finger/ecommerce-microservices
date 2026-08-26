package com.innowise.orderservice.model;

public enum OrderStatus {
    CREATED,

    PAID,

    /**
     * Оплата не прошла. Отдельный статус, а не {@link #CANCELLED}: неудачная попытка
     * оплаты не отменяет заказ -- пользователь может заплатить ещё раз, и следующее
     * событие об успешном платеже переведёт заказ в {@link #PAID}.
     */
    PAYMENT_FAILED,

    SHIPPED,

    DELIVERED,

    CANCELLED
}
