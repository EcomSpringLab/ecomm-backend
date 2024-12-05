package dev.shubham.labs.ecomm.resillience;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static dev.shubham.labs.ecomm.util.Helper.getMostSpecificCauseMessage;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class RetryConfigCustomizer {

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
