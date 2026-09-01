package com.innowise.orderservice.event;

/**
 * Статусы платежа из контракта payment-service. Копия, а не общая библиотека:
 * общий jar на два сервиса связал бы их релизы, а контракт здесь маленький.
 * <p>
 * Перечислены ВСЕ значения продюсера, а не только публикуемые SUCCESS и FAILED:
 * если payment-service когда-нибудь начнёт слать остальные, сообщение должно
 * десериализоваться и осознанно отработать, а не упасть на неизвестном enum и
 * уехать в DLT.
 */
public enum PaymentStatus {

    PENDING,

    SUCCESS,

    FAILED,

    UNDETERMINED
}
