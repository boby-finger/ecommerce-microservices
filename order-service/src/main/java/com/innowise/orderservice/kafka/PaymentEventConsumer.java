package com.innowise.orderservice.kafka;

import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.event.PaymentEventType;
import com.innowise.orderservice.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderPaymentService orderPaymentService;

    /**
     * Группа и стратегия смещений заданы в конфиге:
     * <ul>
     *   <li>{@code group.id = order-service} -- одна группа на сервис. Партиции топика
     *       делятся между экземплярами сервиса, и каждое событие обрабатывается ровно
     *       одним из них; другой потребитель того же топика (например, аналитика) заведёт
     *       свою группу и получит собственную копию потока;</li>
     *   <li>{@code auto-offset-reset = earliest} -- группа, поднявшаяся впервые, читает
     *       топик с начала. Для оплат это безопаснее, чем {@code latest}: пропущенное
     *       событие означает заказ, навсегда застрявший неоплаченным, а повторно
     *       прочитанное -- ничего, обработка идемпотентна;</li>
     *   <li>{@code enable.auto.commit = false} + {@code ack-mode: RECORD} -- смещение
     *       коммитится после успешной обработки каждой записи, а не по таймеру. Это
     *       честный at-least-once: упавший между обработкой и коммитом сервис приведёт к
     *       повторной доставке, что для идемпотентного обработчика безвредно. Автокоммит
     *       по времени дал бы at-most-once и терял бы события;</li>
     *   <li>{@code concurrency = 3} по числу партиций -- по потоку на партицию. Порядок
     *       внутри партиции (то есть по одному заказу) при этом сохраняется.</li>
     * </ul>
     */
    @KafkaListener(topics = "${kafka.topic.payment-events}")
    public void onPaymentEvent(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Received {} from {}-{} at offset {} with key {}",
                event.eventType(), record.topic(), record.partition(), record.offset(), record.key());

        if (event.eventType() != PaymentEventType.CREATE_PAYMENT) {
            // на будущее: чужие типы событий в том же топике пропускаем, а не падаем
            log.warn("Event type {} is not handled by order-service, skipping", event.eventType());
            return;
        }

        orderPaymentService.applyPaymentEvent(event);
    }
}
