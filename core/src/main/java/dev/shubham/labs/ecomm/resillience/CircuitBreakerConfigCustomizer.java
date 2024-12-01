package dev.shubham.labs.ecomm.resillience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class CircuitBreakerConfigCustomizer {

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                var circuitBreaker = entryAddedEvent.getAddedEntry();
                // Add specific event listeners for the added circuit breaker
                circuitBreaker.getEventPublisher()
                        .onStateTransition(stateEvent -> log.info(
                                "Circuit Breaker '{}' transitioned from '{}' to '{}' state.",
                                stateEvent.getCircuitBreakerName(),
                                stateEvent.getStateTransition().getFromState(),
                                stateEvent.getStateTransition().getToState()))
                        .onCallNotPermitted(stateEvent -> log.warn(
                                "Circuit Breaker '{}' is OPEN. Calls are not permitted.",
                                stateEvent.getCircuitBreakerName()))
                        .onFailureRateExceeded(stateEvent -> log.error(
                                "Circuit Breaker '{}' failure rate exceeded the threshold. Current Failure Rate: {}%.",
                                stateEvent.getCircuitBreakerName(),
                                stateEvent.getFailureRate()))
                        .onSlowCallRateExceeded(stateEvent -> log.warn(
                                "Circuit Breaker '{}' slow call rate exceeded the threshold. Current Slow Call Rate: {}%.",
                                stateEvent.getCircuitBreakerName(),
                                stateEvent.getSlowCallRate()));

            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit breaker '{}' removed from registry.", entryRemoveEvent.getRemovedEntry().getName());

            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit breaker '{}' replaced in registry.", entryReplacedEvent.getOldEntry().getName());

            }
        };
    }
}
