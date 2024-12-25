package dev.shubham.labs.kafka.consumer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerManager {
    private static final AttributeKey<String> CONSUMER_ID = AttributeKey.stringKey("kafka.consumer.id");
    private static final AttributeKey<String> TOPIC_NAME = AttributeKey.stringKey("kafka.topic");
    private static final AttributeKey<String> GROUP_ID = AttributeKey.stringKey("kafka.consumer.group.id");
    private static final AttributeKey<String> PREVIOUS_STATE = AttributeKey.stringKey("circuit_breaker.previous_state");
    private static final AttributeKey<String> CURRENT_STATE = AttributeKey.stringKey("circuit_breaker.current_state");
    private final CircuitBreaker circuitBreaker;
    private final ConcurrentMessageListenerContainer<?, ?> container;
    private final Tracer tracer;
    private final MeterProvider meterProvider;
    private final String consumerId;
    private final String topicName;
    private final String groupId;
    private final AtomicReference<CircuitBreaker.State> currentState =
            new AtomicReference<>(CircuitBreaker.State.CLOSED);
    private LongCounter stateTransitionCounter;
    private LongCounter failureCounter;

    // Builder pattern remains the same
    public static Builder builder() {
        return new Builder();
    }

    private Attributes getCommonAttributes() {
        return Attributes.of(
                CONSUMER_ID, consumerId,
                TOPIC_NAME, topicName,
                GROUP_ID, groupId
        );
    }

    public void setup() {
        setupMetrics();
        setupCircuitBreakerEvents();
        logConsumerDetails();
    }

    private void logConsumerDetails() {
        log.info("Setting up CircuitBreakerManager for consumer: {} (topic: {}, group: {})",
                consumerId, topicName, groupId);
    }

    private void setupMetrics() {
        Meter meter = meterProvider.get("kafka.consumer.circuit_breaker");
        Attributes commonAttributes = getCommonAttributes();

        stateTransitionCounter = meter
                .counterBuilder("circuit_breaker.state_transitions")
                .setDescription("Number of circuit breaker state transitions")
                .build();

        failureCounter = meter
                .counterBuilder("circuit_breaker.failures")
                .setDescription("Number of failures recorded by circuit breaker")
                .build();

        // Register initial state metric
        meter.gaugeBuilder("circuit_breaker.state")
                .setDescription("Current state of the circuit breaker")
                .buildWithCallback(
                        measurement -> measurement.record(
                                currentState.get().getOrder(),
                                commonAttributes
                        )
                );
    }

    private void setupCircuitBreakerEvents() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    Span span = tracer.spanBuilder("circuit_breaker.state_transition")
                            .setParent(Context.current())
                            .startSpan();

                    try {
                        handleStateTransition(event.getStateTransition());

                        span.setAllAttributes(Attributes.builder()
                                .putAll(getCommonAttributes())
                                .put(PREVIOUS_STATE, event.getStateTransition().getFromState().name())
                                .put(CURRENT_STATE, event.getStateTransition().getToState().name())
                                .build());

                        stateTransitionCounter.add(1, getCommonAttributes());

                    } finally {
                        span.end();
                    }
                })
                .onError(event -> {
                    Span span = tracer.spanBuilder("circuit_breaker.error")
                            .setParent(Context.current())
                            .startSpan();

                    try {
                        failureCounter.add(1, getCommonAttributes());

                        span.setAllAttributes(Attributes.builder()
                                .putAll(getCommonAttributes())
                                .put("error.type", event.getThrowable().getClass().getName())
                                .put("error.message", event.getThrowable().getMessage())
                                .build());

                        log.error("Circuit breaker error for consumer {} (topic: {}, group: {})",
                                consumerId, topicName, groupId, event.getThrowable());
                    } finally {
                        span.end();
                    }
                })
                .onCallNotPermitted(event -> {
                    log.warn("Circuit breaker rejected call for consumer {} (topic: {}, group: {}) at {}",
                            consumerId, topicName, groupId, event.getCreationTime());
                });
    }

    private void handleStateTransition(CircuitBreaker.StateTransition event) {
        CircuitBreaker.State previousState = currentState.get();
        CircuitBreaker.State newState = event.getToState();

        log.info("Circuit breaker state transition for consumer {} (topic: {}, group: {}): {} -> {}",
                consumerId, topicName, groupId, previousState, newState);

        switch (event) {
            case CLOSED_TO_OPEN, HALF_OPEN_TO_OPEN -> {
                container.pause();
                scheduleHealthCheck();
            }
            case OPEN_TO_HALF_OPEN -> {
                container.resume();
                container.getContainerProperties()
                        .setPollTimeout(Duration.ofMillis(100).toMillis());
            }
            case HALF_OPEN_TO_CLOSED -> {
                container.resume();
                container.getContainerProperties()
                        .setPollTimeout(Duration.ofMillis(1000).toMillis());
            }
        }

        currentState.set(newState);
    }

    private void scheduleHealthCheck() {
        // Implement health check logic if needed
    }

    public CircuitBreaker.State getCurrentState() {
        return currentState.get();
    }

    public <T> T executeWithCircuitBreaker(Supplier<T> supplier, String operationName) {
        Span span = tracer.spanBuilder("circuit_breaker.execute")
                .setParent(Context.current())
                .startSpan();

        try {
            span.setAttribute("operation", operationName);
            span.setAllAttributes(getCommonAttributes());

            return circuitBreaker.executeSupplier(supplier);
        } finally {
            span.end();
        }
    }

    public void executeWithCircuitBreaker(Runnable runnable, String operationName) {
        executeWithCircuitBreaker(() -> {
            runnable.run();
            return null;
        }, operationName);
    }

    public static class Builder {
        private CircuitBreaker circuitBreaker;
        private ConcurrentMessageListenerContainer<?, ?> container;
        private Tracer tracer;
        private MeterProvider meterProvider;
        private String consumerId;
        private String topicName;
        private String groupId;

        public Builder circuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public Builder container(ConcurrentMessageListenerContainer<?, ?> container) {
            this.container = container;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder meterProvider(MeterProvider meterProvider) {
            this.meterProvider = meterProvider;
            return this;
        }

        public Builder consumerId(String consumerId) {
            this.consumerId = consumerId;
            return this;
        }

        public Builder topicName(String topicName) {
            this.topicName = topicName;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public CircuitBreakerManager build() {
            return new CircuitBreakerManager(
                    circuitBreaker,
                    container,
                    tracer,
                    meterProvider,
                    consumerId,
                    topicName,
                    groupId
            );
        }
    }
}