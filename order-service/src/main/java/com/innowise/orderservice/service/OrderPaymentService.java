package com.innowise.orderservice.service;

import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.event.PaymentStatus;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Применение результата платежа к заказу. Вынесено из консьюмера, чтобы транспорт
 * (Kafka) и правила перехода статусов не были перемешаны: те же правила можно будет
 * переиспользовать, если оплата когда-нибудь придёт другим путём.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    /**
     * Статусы, из которых событие оплаты заказ уже не двигает.
     * <p>
     * {@link OrderStatus#PAID} здесь потому, что оплаченный заказ нельзя "разоплатить":
     * если пользователь заплатил повторно и вторая попытка провалилась, заказ обязан
     * остаться оплаченным. {@code SHIPPED}/{@code DELIVERED}/{@code CANCELLED} -- потому
     * что заказ ушёл дальше по собственному жизненному циклу, и решение по нему
     * принимает уже не платёж (успешная оплата отменённого заказа -- это возврат
     * средств, а не смена статуса; такой случай логируется для ручного разбора).
     */
    private static final Set<OrderStatus> NOT_AFFECTED_BY_PAYMENTS =
            EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;

    /**
     * Идемпотентность обеспечивается тем, что обработка события -- это ПРИСВОЕНИЕ
     * статуса, а не его изменение относительно текущего. Kafka даёт at-least-once, и одно
     * и то же событие может приехать дважды: во второй раз заказ уже находится в целевом
     * статусе, метод это видит и ничего не делает. Отдельная таблица обработанных
     * идентификаторов не нужна -- операция и так по построению повторяема.
     * <p>
     * Порядок событий по одному заказу гарантирован ключом сообщения: payment-service
     * кладёт в ключ {@code orderId}, все события заказа попадают в одну партицию, а внутри
     * партиции Kafka сохраняет порядок и отдаёт записи одному потоку консьюмера. Поэтому
     * "старый" FAILED не может примениться после "нового" SUCCESS. Дополнительной
     * страховкой служит {@link #NOT_AFFECTED_BY_PAYMENTS}: даже при повторной доставке
     * старого события оплаченный заказ не будет откачен назад.
     *
     * @return {@code true}, если статус заказа действительно изменился
     */
    @Transactional
    public boolean applyPaymentEvent(PaymentEvent event) {
        OrderStatus target = targetStatus(event.status());
        if (target == null) {
            // Продюсер публикует только SUCCESS и FAILED. Всё остальное (PENDING --
            // платёж ещё в процессе, UNDETERMINED -- итог платежа так и не выяснен)
            // не содержит решения, которое заказ мог бы отразить: перевести его в
            // оплаченный нельзя, объявить оплату неудачной -- тоже. Такое событие
            // подтверждается и не меняет заказ; разбираться с ним -- задача
            // payment-service, а не заказа.
            log.warn("Payment {} for order {} carries status {}, order status left unchanged",
                    event.paymentId(), event.orderId(), event.status());
            return false;
        }

        Optional<Order> found = orderRepository.findById(event.orderId());
        if (found.isEmpty()) {
            // Заказа нет: события об оплате приходят после создания заказа, поэтому это
            // не гонка, а рассогласование данных. Ретраить бессмысленно -- заказ не
            // появится, а застрявшее сообщение заблокировало бы всю партицию, то есть
            // оплату всех заказов, попавших в неё. Логируем и подтверждаем.
            log.warn("Payment {} refers to unknown order {}, event is skipped",
                    event.paymentId(), event.orderId());
            return false;
        }

        Order order = found.get();
        if (Boolean.TRUE.equals(order.getDeleted())) {
            log.warn("Payment {} refers to deleted order {}, event is skipped",
                    event.paymentId(), event.orderId());
            return false;
        }

        OrderStatus current = order.getStatus();
        if (current == target) {
            log.info("Order {} is already in {}, payment event {} changes nothing",
                    order.getId(), current, event.paymentId());
            return false;
        }
        if (NOT_AFFECTED_BY_PAYMENTS.contains(current)) {
            log.warn("Order {} is in {} and is no longer driven by payments, payment {} with "
                            + "status {} is skipped",
                    order.getId(), current, event.paymentId(), event.status());
            return false;
        }

        order.setStatus(target);
        log.info("Order {} moved {} -> {} by payment {} ({})",
                order.getId(), current, target, event.paymentId(), event.status());
        return true;
    }

    private static OrderStatus targetStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == PaymentStatus.SUCCESS) {
            return OrderStatus.PAID;
        }
        if (paymentStatus == PaymentStatus.FAILED) {
            return OrderStatus.PAYMENT_FAILED;
        }
        return null;
    }
}
