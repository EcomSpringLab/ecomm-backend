package dev.shubham.labs.ecomm.kafka.xconsumer.resiliency;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

public class CircuitBreakerStrategy implements ResilienceStrategy {
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerStrategy(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void executeWithResilience(Runnable action) throws Throwable {
        circuitBreaker.executeCheckedRunnable(action::run);
    }
}