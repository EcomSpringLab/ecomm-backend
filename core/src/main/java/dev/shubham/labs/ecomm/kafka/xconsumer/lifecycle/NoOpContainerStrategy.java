package dev.shubham.labs.ecomm.kafka.xconsumer.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.listener.MessageListenerContainer;

@Slf4j
public class NoOpContainerStrategy implements ContainerLifecycleStrategy {
    @Override
    public void registerContainer(MessageListenerContainer container) {
        log.debug("No-op container strategy: nothing to register");
    }

    @Override
    public void onCircuitBreakerOpen(MessageListenerContainer container) {
        log.debug("No-op container strategy: ignoring circuit breaker open");
    }

    @Override
    public void onCircuitBreakerClose(MessageListenerContainer container) {
        log.debug("No-op container strategy: ignoring circuit breaker close");
    }
}
