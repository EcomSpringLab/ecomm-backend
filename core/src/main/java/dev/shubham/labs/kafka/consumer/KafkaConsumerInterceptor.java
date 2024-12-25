package dev.shubham.labs.kafka.consumer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.Arrays;

public class KafkaConsumerInterceptor<K, V> implements RecordInterceptor<K, V> {

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("kafka-consumer");
    private static final ThreadLocal<Span> activeSpan = new ThreadLocal<>();

    @Override
    public void setupThreadState(Consumer<?, ?> consumer) {
        // Initialize thread-local state if necessary
    }

    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        // Clear thread-local span to avoid memory leaks
        activeSpan.remove();
    }

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        // Extract parent context from Kafka headers
        Context parentContext = extractContextFromHeaders(record);

        // Start a new span for processing
        Span span = tracer.spanBuilder("kafka.consume")
                .setParent(parentContext)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();

        // Make the span current for downstream processing
        try (Scope scope = span.makeCurrent()) {
            // Add Kafka-specific attributes
            span.setAttribute("messaging.kafka.topic", record.topic());
            span.setAttribute("messaging.kafka.partition", record.partition());
            span.setAttribute("messaging.kafka.offset", record.offset());

            // Store the span in ThreadLocal for later use
            activeSpan.set(span);

        } catch (Exception e) {
            // If an error occurs during intercept setup, record it
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.end();
            throw e; // Ensure error propagates to the caller
        }

        return record; // Pass the record downstream
    }

    @Override
    public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        // Optionally mark the span as successful
        Span span = activeSpan.get();
        if (span != null) {
            span.setStatus(StatusCode.OK);
        }
    }

    @Override
    public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
        // Record failure on the span
        Span span = activeSpan.get();
        if (span != null) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
        }
    }

    @Override
    public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        // End the span and clean up resources
        Span span = activeSpan.get();
        if (span != null) {
            span.end(); // Properly close the span
        }
        clearThreadState(consumer); // Clear thread-local state
    }

    private Context extractContextFromHeaders(ConsumerRecord<K, V> record) {
        ContextPropagators propagators =
                ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance()));
        return propagators.getTextMapPropagator().extract(Context.current(), record.headers(), new KafkaHeadersGetter());
    }

    private static class KafkaHeadersGetter implements TextMapGetter<Headers> {
        @Override
        public Iterable<String> keys(Headers headers) {
            return Arrays.stream(headers.toArray()).map(Header::key).toList();
        }

        @Override
        public String get(Headers headers, String key) {
            Header header = headers.lastHeader(key);
            return header != null ? new String(header.value()) : null;
        }
    }
}
