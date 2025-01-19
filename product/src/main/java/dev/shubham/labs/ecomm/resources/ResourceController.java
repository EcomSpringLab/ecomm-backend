package dev.shubham.labs.ecomm.resources;

import dev.shubham.labs.ecomm.client.InventoryRestClient;
import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.producer.KafkaProducerService;
import io.opentelemetry.api.OpenTelemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ResourceController {

    private final InventoryRestClient inventoryRestClient;
    private final KafkaProducerService<String, AllocateInventoryEvent> allocateInventoryProducerService;
    private final KafkaTemplate<String, AllocateInventoryEvent> kafkaTemplate;
    private final OpenTelemetry openTelemetry;
//    private final KafkaTemplate<String, String> kafkaTemplateCusTom;

    @GetMapping(path = "/test")
    ResponseEntity<String> status() {
        log.info("status request received");
        var message = MessageBuilder.withPayload(new AllocateInventoryEvent(UUID.randomUUID().toString()))
                .copyHeaders(new HashMap<>())
                .build();
//        kafkaTemplateCusTom.send(message);
//        kafkaTemplate.send(message);
//        kafkaTemplate.send("allocate-inventory-event", new AllocateInventoryEvent(UUID.randomUUID().toString()));
        allocateInventoryProducerService.send(new AllocateInventoryEvent(UUID.randomUUID().toString()));
        return inventoryRestClient.findInventory(Collections.singleton("test"));

    }
}
