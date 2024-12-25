package dev.shubham.labs.ecomm.kafka.consumer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;

@Slf4j
public class KafkaConsumerTracer {
    private final String consumerName;
    private final OpenTelemetry openTelemetry;

    // Initialize tracer once
    private final Tracer tracer;
    // Initialize propagator once
    private final TextMapPropagator propagator;
    private final CircuitBreaker circuitBreaker;

    public KafkaConsumerTracer(String consumerName, OpenTelemetry openTelemetry,CircuitBreaker circuitBreaker) {
        this.consumerName = consumerName;
        this.openTelemetry = openTelemetry;
        this.circuitBreaker = circuitBreaker;
        this.tracer = openTelemetry.getTracer(consumerName);
        // Create composite propagator with both trace context and baggage
        this.propagator = TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance()
        );
    }

    // Getter for Headers specifically
    private static class KafkaHeadersGetter implements TextMapGetter<Headers> {
        @Override
        public Iterable<String> keys(Headers headers) {
            return Arrays.stream(headers.toArray())
                    .map(Header::key)
                    .toList();
        }

        @Override
        public String get(Headers headers, String key) {
            Header header = headers.lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }

    public <K, V> void tracedOperation(ConsumerRecord<K, V> record, Runnable operation) {
        // Extract context using the headers directly
        Context parentContext = extractContext(record.headers());
        Span span = createSpan(record, parentContext);

        try (Scope scope = span.makeCurrent()) {
            circuitBreaker.executeSupplier(() -> {
                operation.run();
                span.setStatus(StatusCode.OK);
                return null;
            });
        } catch (Exception e) {
            handleError(span, record, e);
        } finally {
            span.end();
        }
    }

    private Context extractContext(Headers headers) {
        return propagator.extract(Context.current(), headers, new KafkaHeadersGetter());
    }

    private <K, V> Span createSpan(ConsumerRecord<K, V> record, Context parentContext) {
        return tracer.spanBuilder("kafka.message.process.otel")
                .setParent(parentContext)
                .setSpanKind(SpanKind.CONSUMER)
                .setAllAttributes(createAttributes(record))
                .startSpan();
    }

    private <K, V> Attributes createAttributes(ConsumerRecord<K, V> record) {
        AttributesBuilder builder = Attributes.builder()
                .put(SemanticAttributes.MESSAGING_SYSTEM, "kafka")
                .put(SemanticAttributes.MESSAGING_OPERATION, "receive")
                .put(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
                .put(SemanticAttributes.MESSAGING_DESTINATION_NAME, record.topic())
                .put(SemanticAttributes.MESSAGING_KAFKA_PARTITION, record.partition())
                .put(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset())
                .put(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, consumerName);

        // Add key if present
        if (record.key() != null) {
            builder.put("messaging.kafka.message.key", record.key().toString());
        }

        // Extract and add baggage from context
        Baggage baggage = Baggage.fromContext(Context.current());
        baggage.forEach((key, value) ->
                builder.put("baggage." + key, value.getValue()));

        return builder.build();
    }

    private <K, V> void handleError(Span span, ConsumerRecord<K, V> record, Exception e) {
        span.setStatus(StatusCode.ERROR, e.getMessage());
        span.setAttribute(SemanticAttributes.EXCEPTION_TYPE, e.getClass().getName());
        span.setAttribute(SemanticAttributes.EXCEPTION_MESSAGE, e.getMessage());
        span.recordException(e);

        log.error("Error processing message: topic={}, partition={}, offset={}, traceId={}, spanId={}, error={}",
                record.topic(),
                record.partition(),
                record.offset(),
                span.getSpanContext().getTraceId(),
                span.getSpanContext().getSpanId(),
                e.getMessage());
    }
}
