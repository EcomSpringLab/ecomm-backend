package dev.shubham.labs.ecomm.kafka.xconsumer;

import dev.shubham.labs.ecomm.kafka.xconsumer.event.EventService;
import dev.shubham.labs.ecomm.kafka.xconsumer.resiliency.ResilienceStrategy;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.function.Consumer;

@Slf4j
public class CustomizedMessageListener<K, V> {

    private final EventService<K, V> eventService;
    private final Consumer<ConsumerRecord<K, V>> messageProcessor;
    private final ResilienceStrategy resilienceStrategy;


    public CustomizedMessageListener(EventService<K, V> eventService, ResilienceStrategy resilienceStrategy,
                                     Consumer<ConsumerRecord<K, V>> messageProcessor) {
        this.eventService = eventService;
        this.resilienceStrategy = resilienceStrategy;
        this.messageProcessor = messageProcessor;
    }

    public AcknowledgingMessageListener<K, V> messageListener() {
        return (record, acknowledgment) -> {
            try {
                eventService.saveEvent(record);
                eventService.updateEventStateToProcessing(record);
                resilienceStrategy.executeWithResilience(() -> {
                    messageProcessor.accept(record);
                    eventService.updateEventStateToConsumedSuccessfully(record);
                });
                if(acknowledgment != null)
                    acknowledgment.acknowledge();
            }catch (CallNotPermittedException e) {
                eventService.updateEventStateToConsumedFailure(record, e);
                log.error("call not permitted");
                throw e;
            } catch (Throwable e) {
                if(eventService.isNoOp()){
                    log.error("Error processing message", e);
                    throw new RuntimeException(e);
                }
                eventService.updateEventStateToConsumedFailure(record, e);
                if(acknowledgment != null)
                    acknowledgment.acknowledge();
            }
        };
    }
}
