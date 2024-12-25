package dev.shubham.labs.ecomm.resources;

import dev.shubham.labs.ecomm.client.InventoryRestClient;
import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.KafkaConsumerProps;
import dev.shubham.labs.kafka.consumer.BaseKafkaConsumer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.List;

//@Service
@Slf4j
public class InvnetoryConsumer extends BaseKafkaConsumer<String, AllocateInventoryEvent> {


    protected InvnetoryConsumer(KafkaConsumerProps inventoryConsumerProps, OpenTelemetry openTelemetry, MeterRegistry meterRegistry,
                                CircuitBreakerRegistry circuitBreakerRegistry, InventoryRestClient inventoryRestClient) {
        super(inventoryConsumerProps, openTelemetry, meterRegistry,
                circuitBreakerRegistry.circuitBreaker("backendB"),
                (key, value) -> {
                    log.info("Received message: {} -> {}", key, value);
                    inventoryRestClient.findInventory(List.of(value.beerId()));
                },
                StringDeserializer.class, JsonDeserializer.class);
    }

    @PostConstruct
    public void init() {
        this.start();
    }

    @PreDestroy
    public void destroy() {
        this.stop();
    }
}
