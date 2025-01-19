package dev.shubham.labs.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.shubham.labs.Helper.getMostSpecificCauseMessage;

@AutoConfiguration
public class ResilienceAutoConfiguration {

//    @Bean
//    public CompositeCustomizer<CircuitBreakerConfigCustomizer> circuitBreakerRegistryCustomizer() {
//        return registry -> {
//                CompositeCustomizer compositeRegistry = (CompositeCustomizer) registry;
//                compositeRegistry.withRegistryStore(new CacheCircuitBreakerRegistryStore());
//            }
//        };
//    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    static class CircuitBreakerConfiguration {

        @Bean
        public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
            return new RegistryEventConsumer<>() {
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

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    static class RetryConfiguration {
        @Bean
        public RegistryEventConsumer<Retry> retryEventConsumer() {
            return new RegistryEventConsumer<>() {
                @Override
                public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                    var retry = entryAddedEvent.getAddedEntry();
                    // Add specific event listeners for the added retry
                    retry.getEventPublisher()
                            .onRetry(retryEvent -> log.warn("Retry '{}' | Attempt #{} | Error: {}",
                                    retryEvent.getName(), retryEvent.getNumberOfRetryAttempts(), getMostSpecificCauseMessage.apply(retryEvent.getLastThrowable())))
                            .onSuccess(successEvent -> log.info("Retry '{}' | Succeeded on attempt #{}",
                                    successEvent.getName(), successEvent.getNumberOfRetryAttempts()))
                            .onError(errorEvent -> log.error("Retry '{}' | Failed after {} attempts | Error: {}",
                                    errorEvent.getName(), errorEvent.getNumberOfRetryAttempts(), getMostSpecificCauseMessage.apply(errorEvent.getLastThrowable())));
                }

                @Override
                public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {
                    log.info("Retry '{}' removed from registry.", entryRemoveEvent.getRemovedEntry().getName());
                }

                @Override
                public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
                    log.info("Retry '{}' replaced in the registry.", entryReplacedEvent.getOldEntry().getName());
                }
            };
        }
    }
}
