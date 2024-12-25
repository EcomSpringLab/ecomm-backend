package dev.shubham.labs.kafka.consumer;

import dev.shubham.labs.kafka.KafkaConsumerProps;
import dev.shubham.labs.kafka.Record;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;


@Getter
@Slf4j
public abstract class KafkaConsumerConfig<K, V extends Record<K>> {

    // Extractor for Kafka headers
    private static final TextMapGetter<ConsumerRecord<?, ?>> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
            return () -> StreamSupport.stream(carrier.headers().spliterator(), false)
                    .map(Header::key)
                    .iterator();
        }

        @Override
        public String get(ConsumerRecord<?, ?> carrier, String key) {
            Header header = carrier.headers().lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    };
    private final ConcurrentMessageListenerContainer<K, V> container;
    private final CircuitBreaker circuitBreaker;
    private final BiConsumer<K, V> messageProcessor;
//    private final Propagator propagator;
    //    private final OpenTelemetry openTelemetry;
//    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;

    protected KafkaConsumerConfig(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value,
                                  MeterRegistry meterRegistry, CircuitBreaker circuitBreaker, BiConsumer<K, V> messageProcessor, ObservationRegistry observationRegistry) {
//        this.openTelemetry = openTelemetry;
//        this.tracer = openTelemetry.getTracer(getClass().getName());
        this.observationRegistry = observationRegistry;
        this.circuitBreaker = circuitBreaker;
        this.messageProcessor = messageProcessor;
        this.container = createContainer(kafkaProperties, key, value, meterRegistry);
        setupCircuitBreakerListener();

    }

    public ConsumerFactory<K, V> consumerFactory(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value) {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(kafkaProperties, key, value));
    }

    public Map<String, Object> consumerConfigs(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, key);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, value);
        // Error handling deserializer configuration
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return props;
    }

    private void setupCircuitBreakerListener() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    String state = event.getStateTransition().toString();

                    Observation.createNotStarted("circuit.breaker.transition", observationRegistry)
                            .lowCardinalityKeyValue("state.transition", state)
                            .observe(() -> {
                                if (event.getStateTransition() == CircuitBreaker.StateTransition.CLOSED_TO_OPEN) {
                                    log.warn("Circuit breaker opened, pausing Kafka consumer");
                                    container.pause();
                                } else if (event.getStateTransition() == CircuitBreaker.StateTransition.OPEN_TO_HALF_OPEN ||
                                        event.getStateTransition() == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
                                    log.info("Circuit breaker closed, resuming Kafka consumer");
                                    container.resume();
                                }
                            });
                });
    }

    private ConcurrentMessageListenerContainer<K, V> createContainer(KafkaConsumerProps config, Class<?> key, Class<?> value,
                                                                     MeterRegistry meterRegistry) {
        ContainerProperties props = new ContainerProperties(config.getTopic());
        props.setMessageListener(createMessageListener());
        props.setObservationEnabled(true);

        var cf = consumerFactory(config, key, value);
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        ConcurrentMessageListenerContainer<K, V> container =
                new ConcurrentMessageListenerContainer<>(cf, props);
        container.setConcurrency(config.getConcurrency());

        return container;
    }

    public void start() {
        Observation.createNotStarted("kafka.consumer.start", observationRegistry)
                .observe(container::start);
    }

    public void stop() {
        Observation.createNotStarted("kafka.consumer.stop", observationRegistry)
                .observe(() -> container.stop());
    }

    private void handleError(ConsumerRecord<K, V> record, Exception e, Observation observation) {
        observation.error(e);

        log.error("Error processing message: topic={}, partition={}, offset={}, error={}",
                record.topic(),
                record.partition(),
                record.offset(),
                e.getMessage());
    }

    private void processMessageWithCircuitBreaker(ConsumerRecord<K, V> record, Observation parentObservation) {
        Observation processObservation = Observation.createNotStarted("process.message", observationRegistry)
                .parentObservation(parentObservation)
                .lowCardinalityKeyValue("operation", "process");

        processObservation.observe(() -> {
            try {
                circuitBreaker.executeSupplier(() -> {
                    messageProcessor.accept(record.key(), record.value());
                    return null;
                });
            } catch (Exception e) {
                handleError(record, e, processObservation);
                throw e;
            }
        });
    }

    private MessageListener<K, V> createMessageListener() {
        return record -> {

            // Extract trace context and baggage from record headers
//            Context extractedContext = propagator
//                    .extract(Context.current(), record, HEADER_GETTER);
            // Create observation for the message processing
            Observation observation = Observation.createNotStarted("kafka.message.process", observationRegistry)
                    .lowCardinalityKeyValue("messaging.system", "kafka")
                    .lowCardinalityKeyValue("messaging.operation", "receive")
                    .lowCardinalityKeyValue("messaging.destination.kind", "topic")
                    .highCardinalityKeyValue("messaging.kafka.topic", record.topic())
                    .highCardinalityKeyValue("messaging.kafka.partition", String.valueOf(record.partition()))
                    .highCardinalityKeyValue("messaging.kafka.offset", String.valueOf(record.offset()));

            if (record.key() != null) {
                observation.highCardinalityKeyValue("messaging.kafka.key", record.key().toString());
            }

            observation.observe(() -> {
                try {
                    processMessageWithCircuitBreaker(record, observation);
                } catch (Exception e) {
                    observation.error(e);
//                    throw e;
                }
            });
        };
    }


//    private record SpanInfo(Span span, Scope scope) implements AutoCloseable {
//        @Override
//        public void close() {
//            scope.close();
//            span.end();
//        }
//    }
//
//
//
//    // Improved span creation with better error handling
//    protected SpanInfo createSpan(String name, Context parentContext, SpanKind kind) {
//        SpanBuilder spanBuilder = tracer.spanBuilder(name)
//                .setSpanKind(kind)
//                .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka");
//
//        if (parentContext != null) {
//            spanBuilder.setParent(parentContext);
//        }
//
//        Span span = spanBuilder.startSpan();
//        return new SpanInfo(span, span.makeCurrent());
//    }
//    // Enhanced error handling with detailed attributes
//    private void handleProcessingError(Span span, ConsumerRecord<K, V> record, Throwable error) {
//        span.setStatus(StatusCode.ERROR);
//        span.recordException(error,
//                Attributes.builder()
//                        .put("error.type", error.getClass().getName())
//                        .put("messaging.kafka.topic", record.topic())
//                        .put("messaging.kafka.partition", record.partition())
//                        .put("messaging.kafka.offset", record.offset())
//                        .build());
//
//        log.error("Error processing message: topic={}, partition={}, offset={}, traceId={}, spanId={}, error={}",
//                record.topic(),
//                record.partition(),
//                record.offset(),
//                span.getSpanContext().getTraceId(),
//                span.getSpanContext().getSpanId(),
//                error.getMessage());
//    }
//
//    // Enhanced processing with dedicated span
//    private void processMessageWithTracing(ConsumerRecord<K, V> record, Context parentContext) {
//        try (var processSpan = createSpan("process.message", parentContext, SpanKind.INTERNAL)) {
//            Span span = processSpan.span();
//            span.setAttribute("messaging.operation", "process");
//
//            try {
//                circuitBreaker.executeSupplier(() -> {
//                    messageProcessor.accept(record.key(), record.value());
//                    return null;
//                });
//                span.setStatus(StatusCode.OK);
//            } catch (Exception e) {
//                handleProcessingError(span, record, e);
//                throw e;
//            }
//        }
//    }
//
//    // Enhanced context extraction with validation
//    private Context extractContext(ConsumerRecord<K, V> record) {
//        try {
//            return openTelemetry.getPropagators().getTextMapPropagator()
//                    .extract(Context.current(), record, CONTEXT_GETTER);
//        } catch (Exception e) {
//            log.warn("Failed to extract trace context from record: {}", e.getMessage());
//            return Context.current();
//        }
//    }
//
//    protected void handleError(Span span, Throwable error, String message, Object... args) {
//        span.setStatus(StatusCode.ERROR, error.getMessage());
//        span.recordException(error);
//        log.error(message + " traceId={}, spanId",
//                Stream.concat(Stream.of(args),
//                                Stream.of(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId()))
//                        .toArray());
//    }
//
//    // Enhanced message listener with better context handling
//    private MessageListener<K, V> createMessageListener() {
//        return record -> {
//            // Extract context with fallback
//            Context extractedContext = extractContext(record);
//            Context parentContext = extractedContext != null ? extractedContext : Context.current();
//
//            try (var consumerSpan = createConsumerSpan(record, parentContext)) {
////                MDC.put("traceId", consumerSpan.span().getSpanContext().getTraceId());
////                MDC.put("spanId", consumerSpan.span().getSpanContext().getSpanId());
//                log.info("starting consuming");
//
//                try {
//                    processMessageWithTracing(record, Context.current());
//                } catch (Exception e) {
//                    handleProcessingError(consumerSpan.span(), record, e);
////                    throw e; // Rethrow for retry/DLQ handling
//                } finally {
////                    MDC.clear();
//                }
//            }
//        };
//    }
//
//    // Enhanced span creation with more semantic conventions
//    private SpanInfo createConsumerSpan(ConsumerRecord<K, V> record, Context parentContext) {
//        SpanInfo spanInfo = createSpan("kafka.consume", parentContext, SpanKind.CONSUMER);
//        Span span = spanInfo.span();
//
//        // Add standard OpenTelemetry semantic attributes
//        span.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "kafka")
//                .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
//                .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")
//                .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, record.topic())
//                .setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, record.partition())
//                .setAttribute(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset())
//                .setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, String.format("%d-%d", record.partition(), record.offset()))
//                .setAttribute("messaging.kafka.consumer.group",container.getContainerProperties().getGroupId());
//
//        // Add custom attributes if needed
//        if (record.key() != null) {
//            span.setAttribute("messaging.kafka.message.key", record.key().toString());
//        }
//
//        return spanInfo;
//    }
//
//    // Enhanced TextMapGetter with better null handling
//    private static final TextMapGetter<ConsumerRecord<?, ?>> CONTEXT_GETTER = new TextMapGetter<>() {
//        @Override
//        public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
//            return () -> StreamSupport.stream(carrier.headers().spliterator(), false)
//                    .map(Header::key)
//                    .filter(key -> key.startsWith("traceparent") || key.startsWith("tracestate"))
//                    .iterator();
//        }
//
//        @Override
//        public String get(ConsumerRecord<?, ?> carrier, String key) {
//            Header header = carrier.headers().lastHeader(key);
//            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
//        }
//    };


}
