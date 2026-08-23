package com.innowise.paymentservice.mapper;

import com.innowise.paymentservice.dto.PaymentRequestDto;
import com.innowise.paymentservice.dto.PaymentResponseDto;
import com.innowise.paymentservice.event.PaymentEvent;
import com.innowise.paymentservice.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timestamp", ignore = true)
    Payment toEntity(PaymentRequestDto dto);

    PaymentResponseDto toDto(Payment payment);

    @Mapping(target = "eventType", constant = "CREATE_PAYMENT")
    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "orderId", source = "payment.orderId")
    @Mapping(target = "userId", source = "payment.userId")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(target = "paymentAmount", source = "payment.paymentAmount")
    @Mapping(target = "timestamp", source = "payment.timestamp")
    @Mapping(target = "occurredAt", source = "occurredAt")
    PaymentEvent toEvent(Payment payment, Instant occurredAt);
}
