package dev.shubham.labs.kafka.producer;

import dev.shubham.labs.kafka.Record;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.function.BiConsumer;

public interface KafkaProducerService<K, V extends Record<K>> {

    void send(V value);

    void send(V value, Map<String, Object> header);

    void send(V value, BiConsumer<SendResult<K, V>, ? super Throwable> action);

    void send(V value, Map<String, Object> header, BiConsumer<SendResult<K, V>, ? super Throwable> action);

}
