package dev.shubham.labs.ecomm.kafka.xconsumer.resiliency;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

public class ResilienceStrategyFactory {
    public static ResilienceStrategy createStrategy(
            CircuitBreaker circuitBreaker,
            Retry retry) {
        if (circuitBreaker != null && retry != null) {
            return new CircuitBreakerStrategy(circuitBreaker)
                    .andThen(new RetryStrategy(retry));
        } else if (circuitBreaker != null) {
            return new CircuitBreakerStrategy(circuitBreaker);
        } else if (retry != null) {
            return new RetryStrategy(retry);
        }
        return new NoOpResilienceStrategy();
    }
}