package dev.shubham.labs.ecomm.resources;

import dev.shubham.labs.ecomm.client.InventoryRestClient;
import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ResourceController {

    private final InventoryRestClient inventoryRestClient;
    private final KafkaProducerService<String, AllocateInventoryEvent> allocateInventoryProducerService;

    @GetMapping(path = "/test")
    ResponseEntity<String> status() {
        log.info("status request received");
        allocateInventoryProducerService.send(new AllocateInventoryEvent(UUID.randomUUID().toString()));
        return inventoryRestClient.findInventory(Collections.singleton("test"));

    }
}
