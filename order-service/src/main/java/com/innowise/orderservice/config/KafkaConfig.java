package com.innowise.orderservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long RETRY_ATTEMPTS = 2L;

    /**
     * Имя dead letter topic задаётся явно, а не берётся из дефолта Spring Kafka:
     * дефолт менялся между версиями, а имя топика -- часть эксплуатационного контракта,
     * по которому настроены алерты и разбор.
     */
    private static final String DLT_SUFFIX = "-dlt";

    /**
     * Стратегия для сообщений, которые не удалось обработать: несколько быстрых повторов,
     * дальше -- dead letter topic {@code <topic>-dlt}.
     * <p>
     * Почему не бесконечный retry: консьюмер читает партицию строго по порядку, поэтому
     * сообщение, которое не удаётся обработать, встаёт пробкой перед всеми следующими --
     * один "ядовитый" платёж заморозил бы оплату всех заказов своей партиции. Почему не
     * "залогировать и выбросить": событие об оплате -- это деньги, терять его молча
     * нельзя. DLT совмещает и то и другое: очередь едет дальше, а проблемное сообщение
     * лежит в отдельном топике вместе с заголовками об исходной партиции, смещении и
     * тексте ошибки, и его можно разобрать и переиграть.
     * <p>
     * Короткая серия повторов ({@value #RETRY_ATTEMPTS} повтора с интервалом
     * {@value #RETRY_INTERVAL_MS} мс) нужна для сбоев, которые проходят сами: моргнувшая
     * база, дедлок на строке. Ошибки разбора сообщения {@link DefaultErrorHandler}
     * повторять не будет -- {@code DeserializationException} в его списке фатальных,
     * такое сообщение уходит в DLT сразу, потому что от повтора оно не станет валидным.
     * <p>
     * Для учебного проекта этого достаточно; в бою сюда добавились бы экспоненциальный
     * backoff и алерт на непустой DLT.
     */
    @Bean
    public DefaultErrorHandler paymentEventErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        // DeadLetterPublishingRecoverer публикует значение исходной записи; при ошибке
        // разбора это сырые байты, при ошибке обработки -- уже разобранное событие,
        // поэтому продюсер для DLT настроен тем же JSON-сериализатором.
        // partition = -1: партицию для DLT выбирает продюсер. По умолчанию рекаверер
        // пишет в партицию с тем же номером, что у исходной записи, и падает, если в DLT
        // партиций меньше, чем в исходном топике.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
    }
}
