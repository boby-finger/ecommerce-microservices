package com.innowise.orderservice.service;

import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.event.PaymentEventType;
import com.innowise.orderservice.event.PaymentStatus;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderPaymentService orderPaymentService;

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("149.99"));
        order.setDeleted(false);
        return order;
    }

    private PaymentEvent event(UUID orderId, PaymentStatus status) {
        return new PaymentEvent(
                PaymentEventType.CREATE_PAYMENT,
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                status,
                new BigDecimal("149.99"),
                Instant.parse("2026-01-10T12:00:00Z"),
                Instant.parse("2026-01-10T12:00:01Z"));
    }

    private boolean apply(Order order, PaymentStatus status) {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return orderPaymentService.applyPaymentEvent(event(order.getId(), status));
    }

    @Test
    void successfulPaymentMakesCreatedOrderPaid() {
        Order order = order(OrderStatus.CREATED);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void failedPaymentMovesCreatedOrderToPaymentFailed() {
        Order order = order(OrderStatus.CREATED);

        assertThat(apply(order, PaymentStatus.FAILED)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void successfulPaymentAfterFailedOneMakesOrderPaid() {
        Order order = order(OrderStatus.PAYMENT_FAILED);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void repeatedSuccessfulPaymentLeavesPaidOrderUntouched() {
        Order order = order(OrderStatus.PAID);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void repeatedFailedPaymentLeavesPaymentFailedOrderUntouched() {
        Order order = order(OrderStatus.PAYMENT_FAILED);

        assertThat(apply(order, PaymentStatus.FAILED)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void failedPaymentDoesNotDowngradePaidOrder() {
        Order order = order(OrderStatus.PAID);

        assertThat(apply(order, PaymentStatus.FAILED)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void paymentDoesNotChangeShippedOrder() {
        Order order = order(OrderStatus.SHIPPED);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void paymentDoesNotChangeDeliveredOrder() {
        Order order = order(OrderStatus.DELIVERED);

        assertThat(apply(order, PaymentStatus.FAILED)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void paymentDoesNotChangeCancelledOrder() {
        Order order = order(OrderStatus.CANCELLED);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paymentDoesNotChangeDeletedOrder() {
        Order order = order(OrderStatus.CREATED);
        order.setDeleted(true);

        assertThat(apply(order, PaymentStatus.SUCCESS)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void paymentForUnknownOrderIsSkipped() {
        UUID unknownOrderId = UUID.randomUUID();
        when(orderRepository.findById(unknownOrderId)).thenReturn(Optional.empty());

        assertThat(orderPaymentService.applyPaymentEvent(event(unknownOrderId, PaymentStatus.SUCCESS)))
                .isFalse();
    }

    @Test
    void pendingPaymentIsIgnoredWithoutLookingUpTheOrder() {
        assertThat(orderPaymentService.applyPaymentEvent(
                event(UUID.randomUUID(), PaymentStatus.PENDING))).isFalse();

        verifyNoInteractions(orderRepository);
    }

    @Test
    void undeterminedPaymentIsIgnoredWithoutLookingUpTheOrder() {
        assertThat(orderPaymentService.applyPaymentEvent(
                event(UUID.randomUUID(), PaymentStatus.UNDETERMINED))).isFalse();

        verifyNoInteractions(orderRepository);
    }
}
