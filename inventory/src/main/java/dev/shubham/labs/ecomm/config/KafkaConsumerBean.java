package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.ecomm.kafka.AllocateInventoryEvent;
import dev.shubham.labs.ecomm.kafka.KafkaConsumerProps;
import dev.shubham.labs.ecomm.kafka.consumer.KafkaConsumerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerBean {

    @Bean
    @ConfigurationProperties(prefix = "application.consumer.inventory")
    public KafkaConsumerProps inventoryConsumerProps() {
        return new KafkaConsumerProps();
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, AllocateInventoryEvent>> inventoryConsumerContainer(
            MeterRegistry meterRegistry, KafkaConsumerProps inventoryConsumerProps, CircuitBreakerRegistry circuitBreakerRegistry) {
        return new KafkaConsumerConfig<String, AllocateInventoryEvent>(inventoryConsumerProps, StringDeserializer.class,
                JsonDeserializer.class, meterRegistry, circuitBreakerRegistry) {
        }.getKafkaListenerContainerFactory();
    }

}
