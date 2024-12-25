package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.KafkaConsumerProps;
import dev.shubham.labs.kafka.xconsumer.KafkaConsumerBuilder;
import dev.shubham.labs.kafka.xconsumer.event.EventService;
import dev.shubham.labs.kafka.xconsumer.lifecycle.ContainerStrategyFactory;
import dev.shubham.labs.kafka.xconsumer.resiliency.ResilienceStrategyFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@Slf4j
public class KafkaConsumerBean {

    @Bean
    @ConfigurationProperties(prefix = "application.consumer.inventory")
    public KafkaConsumerProps inventoryConsumerProps() {
        return new KafkaConsumerProps();
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, AllocateInventoryEvent> inventoryConsumer(
            KafkaConsumerProps kafkaProps, MeterRegistry meterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry registry, EventService<String, AllocateInventoryEvent> jpaEventService) {
        return new KafkaConsumerBuilder<String, AllocateInventoryEvent>()
                .withConfig(kafkaProps)
                .withKeyClass(StringDeserializer.class)
                .withValueClass(JsonDeserializer.class)
                .withMeterRegistry(meterRegistry)
                .withEventService(jpaEventService)
                .withResilienceStrategy(ResilienceStrategyFactory
                        .createStrategy(circuitBreakerRegistry.circuitBreaker("backendB"), null))
                .withContainerStrategy(ContainerStrategyFactory.create(circuitBreakerRegistry.circuitBreaker("backendB")))
                .withMessageProcessor((record) -> {
                    log.info("Received message:offset:: {} , partition:: {}", record.offset(), record.partition());
//                throw new RuntimeException("test exception");

                })
                .withAdditionalProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10)
                .build();
    }

    @Bean
    public NewTopic createTopic() {
        return new NewTopic("allocate-inventory-event", 3, (short) 1); // Topic name, partitions, replication factor
    }


}
