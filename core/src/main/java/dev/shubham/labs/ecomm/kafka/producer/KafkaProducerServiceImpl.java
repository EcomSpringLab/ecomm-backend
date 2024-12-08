package dev.shubham.labs.ecomm.kafka.producer;

import dev.shubham.labs.ecomm.kafka.KafkaProducerProps;
import dev.shubham.labs.ecomm.kafka.Record;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
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
                                       MeterRegistry meterRegistry, ObservationRegistry registry, ExecutorService kafkaProducerExecutor, Tracer tracer) {
        super(kafkaProperties, serializer, deSerializer, meterRegistry, registry, kafkaProducerExecutor, tracer);
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

        // Create a span with more detailed configuration
        Span span = tracer.spanBuilder("kafka-produce")
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
        try {
            // Add span attributes
            span.setAttribute("messaging.system", "kafka");
            span.setAttribute("messaging.destination", kafkaTemplate.getDefaultTopic());
            span.setAttribute("messaging.destination_kind", "topic");
            // Inject trace context into Kafka headers
            Context currentContext = Context.current().with(span);

            var mutableHeaders = generateHeaders(value, header);

            ContextPropagators propagators =
                    ContextPropagators.create(
                            TextMapPropagator.composite(
                                    W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()));

            propagators.getTextMapPropagator().inject(currentContext, mutableHeaders, new KafkaHeaderSetter());

            // var observation = registry.getCurrentObservation();
            var message = MessageBuilder.withPayload(value).copyHeaders(mutableHeaders)
                    .build();

            var future = getKafkaTemplate()
                    .send(message);
            future.whenCompleteAsync(action,
                    ContextSnapshotFactory.builder().build().captureAll().wrapExecutor(kafkaProducerExecutor));

        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            // End the span
            span.end();
        }


    }

    Map<String, Object> generateHeaders(V value, Map<String, Object> customHeaders) {
        var header = new HashMap<String, Object>();
        header.put(KafkaHeaders.KEY, value.key());
//        header.put("X-B3-TraceId", Span.current().getSpanContext().getTraceId());
//        header.put("X-B3-SpanId", Span.current().getSpanContext().getSpanId());
        header.putAll(customHeaders);
        return header;
    }

    private static class KafkaHeaderSetter implements TextMapSetter<Map<String, Object>> {
        @Override
        public void set(Map<String, Object> headers, String key, String value) {
            if (key != null && value != null) {
                headers.put(key, value.getBytes());
            }
        }
    }

}
