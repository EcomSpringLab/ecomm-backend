package dev.shubham.labs.ecomm.kafka.xconsumer.lifecycle;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerStrategyFactory {
    public static ContainerLifecycleStrategy create(Map<String, CircuitBreaker> circuitBreakers) {
        return circuitBreakers != null && !circuitBreakers.isEmpty()
                ? new MultiCircuitBreakerContainerStrategy(circuitBreakers)
                : new NoOpContainerStrategy();
    }

    public static ContainerLifecycleStrategy create(CircuitBreaker singleBreaker) {
        if (singleBreaker != null) {
            Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
            breakers.put("default", singleBreaker);
            return new MultiCircuitBreakerContainerStrategy(breakers);
        }
        return new NoOpContainerStrategy();
    }
}