package com.innowise.orderservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Контракт события payment-service. Продюсер отключил type-заголовки, поэтому тип
 * задаётся на стороне консьюмера ({@code spring.json.value.default.type}), а не
 * приходит в сообщении -- это и позволяет иметь собственный класс в своём пакете.
 *
 * @param timestamp  время платежа
 * @param occurredAt время, когда платёж получил финальный статус
 */
public record PaymentEvent(
        PaymentEventType eventType,
        UUID paymentId,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        BigDecimal paymentAmount,
        Instant timestamp,
        Instant occurredAt
) {
}
