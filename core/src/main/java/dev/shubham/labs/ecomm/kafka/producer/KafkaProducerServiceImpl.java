package dev.shubham.labs.ecomm.kafka.producer;

import dev.shubham.labs.ecomm.kafka.KafkaProducerProps;
import dev.shubham.labs.ecomm.kafka.Record;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

@Slf4j
public abstract class KafkaProducerServiceImpl<K, V extends Record<K>> extends KafkaProducerConfig<K, V>
        implements KafkaProducerService<K, V> {

    private final BiConsumer<SendResult<K, V>, ? super Throwable> defaultAction = (result, ex) -> {
        // registry.setCurrentObservationScope(observation.openScope());
        if (ex == null) {
            log.info("Sent message=[{}] with offset=[{}] and partition=[{}]", result.getProducerRecord().value(),
                    result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
        } else {
            log.error("Unable to send message=[{}] due to : {}", result.getProducerRecord().value(), ex.getMessage());
        }
    };

    protected KafkaProducerServiceImpl(KafkaProducerProps kafkaProperties, Class<?> serializer, Class<?> deSerializer,
                                       MeterRegistry meterRegistry, ObservationRegistry registry, ExecutorService kafkaProducerExecutor) {
        super(kafkaProperties, serializer, deSerializer, meterRegistry, registry, kafkaProducerExecutor);
    }

    @Override
    public void send(V value) {
        send(value, new HashMap<>(), defaultAction);
    }

    @Override
    public void send(V value, Map<String, Object> header) {
        send(value, header, defaultAction);
    }

    @Override
    public void send(V value, BiConsumer<SendResult<K, V>, ? super Throwable> action) {
        send(value, new HashMap<>(), action);
    }

    @Override
    public void send(V value, Map<String, Object> header, BiConsumer<SendResult<K, V>, ? super Throwable> action) {

        // var observation = registry.getCurrentObservation();
        var future = getKafkaTemplate()
                .send(MessageBuilder.withPayload(value).copyHeaders(generateHeaders(value, header)).build());
        future.whenCompleteAsync(action,
                ContextSnapshotFactory.builder().build().captureAll().wrapExecutor(kafkaProducerExecutor));

    }

    Map<String, Object> generateHeaders(V value, Map<String, Object> customHeaders) {
        var header = new HashMap<String, Object>();
        header.put(KafkaHeaders.KEY, value.key());
//        header.put("X-B3-TraceId", Span.current().getSpanContext().getTraceId());
//        header.put("X-B3-SpanId", Span.current().getSpanContext().getSpanId());
        header.putAll(customHeaders);
        return header;
    }

}
