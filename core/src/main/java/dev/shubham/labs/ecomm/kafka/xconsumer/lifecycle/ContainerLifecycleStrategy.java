package dev.shubham.labs.ecomm.kafka.xconsumer.lifecycle;

import org.springframework.kafka.listener.MessageListenerContainer;

public interface ContainerLifecycleStrategy {
    void registerContainer(MessageListenerContainer container);
    void onCircuitBreakerOpen(MessageListenerContainer container);

    void onCircuitBreakerClose(MessageListenerContainer container);
}