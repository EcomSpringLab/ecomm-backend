package dev.shubham.labs.kafka.xconsumer.lifecycle;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MultiCircuitBreakerContainerStrategy implements ContainerLifecycleStrategy {
    private final Map<String, CircuitBreaker> circuitBreakers;
    private final AtomicInteger openCircuitCount = new AtomicInteger(0);
    private volatile MessageListenerContainer container;

    public MultiCircuitBreakerContainerStrategy(Map<String, CircuitBreaker> circuitBreakers) {
        this.circuitBreakers = new ConcurrentHashMap<>(circuitBreakers);
    }

    @Override
    public void registerContainer(MessageListenerContainer container) {
        this.container = container;
        setupCircuitBreakerListeners();

        // Check initial states of all circuit breakers
        checkInitialStates();
    }

    private void checkInitialStates() {
        for (CircuitBreaker breaker : circuitBreakers.values()) {
            if (breaker.getState() == CircuitBreaker.State.OPEN) {
                openCircuitCount.incrementAndGet();
            }
        }

        if (openCircuitCount.get() > 0) {
            onCircuitBreakerOpen(container);
        }
    }

    private void setupCircuitBreakerListeners() {
        circuitBreakers.forEach((name, breaker) -> {
            breaker.getEventPublisher()
                    .onStateTransition(event -> {
                        if (container != null) {
                            handleStateTransition(name, event.getStateTransition());
                        }
                    });
        });
    }

    private synchronized void handleStateTransition(String breakerName, CircuitBreaker.StateTransition transition) {
        switch (transition) {
            case CLOSED_TO_OPEN, HALF_OPEN_TO_OPEN -> {
                int openCount = openCircuitCount.incrementAndGet();
                log.warn("Circuit breaker {} opened. Total open circuits: {}", breakerName, openCount);
                if (openCount == 1) { // First circuit breaker to open
                    onCircuitBreakerOpen(container);
                }
            }
            case OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED -> {
                int openCount = openCircuitCount.decrementAndGet();
                log.info("Circuit breaker {} closed/half-open. Remaining open circuits: {}", breakerName, openCount);
                if (openCount == 0) { // All circuit breakers are closed
                    onCircuitBreakerClose(container);
                }
            }
        }
    }

    @Override
    public void onCircuitBreakerOpen(MessageListenerContainer container) {
        log.warn("At least one circuit breaker is open, pausing container");
        container.pause();
    }

    @Override
    public void onCircuitBreakerClose(MessageListenerContainer container) {
        log.info("All circuit breakers closed, resuming container");
        container.resume();
    }
}
