package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.KafkaProducerConfig;
import dev.shubham.labs.kafka.producer.KafkaProducerService;
import dev.shubham.labs.kafka.producer.KafkaProducerServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProducerConfig.class)
public class ProducerConfig {

    @Bean
    public ExecutorService kafkaProducerExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("kafka-producer-", 0L).factory());
    }

    @Bean
    public KafkaProducerService<String, AllocateInventoryEvent> allocateInventoryProducerService(
            KafkaProducerConfig kafkaProducerConfig, MeterRegistry meterRegistry, ObservationRegistry registry,
            ExecutorService kafkaProducerExecutor, Tracer tracer) {
        return new KafkaProducerServiceImpl<>(kafkaProducerConfig.getInstances().get("inventory"), StringSerializer.class, JsonSerializer.class,
                meterRegistry, registry, kafkaProducerExecutor, tracer) {
        };
    }

}
