package dev.shubham.labs.kafka.xconsumer.lifecycle;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.MessageListenerContainer;

@Slf4j
public class CircuitBreakerContainerStrategy implements ContainerLifecycleStrategy {
    private final CircuitBreaker circuitBreaker;
    private volatile MessageListenerContainer container;

    public CircuitBreakerContainerStrategy(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void registerContainer(MessageListenerContainer container) {
        this.container = container;
        setupCircuitBreakerListener();

        //check initial state of circuit breaker
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            onCircuitBreakerOpen(container);
        }
    }


    private void setupCircuitBreakerListener() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {

                    switch (event.getStateTransition()) {
                        case CLOSED_TO_OPEN, HALF_OPEN_TO_OPEN -> {
                            onCircuitBreakerOpen(container);
                        }
                        case OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED -> {
                            onCircuitBreakerClose(container);
                        }
                    }
                });
    }

    @Override
    public void onCircuitBreakerOpen(MessageListenerContainer container) {
        log.warn("Circuit breaker opened, pausing container");
        container.pause();
        container.getContainerProperties().setNoPollThreshold(1);

    }

    @Override
    public void onCircuitBreakerClose(MessageListenerContainer container) {
        log.info("Circuit breaker closed, resuming container");
        container.resume();
    }
}