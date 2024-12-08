package dev.shubham.labs.ecomm.resources;

import dev.shubham.labs.ecomm.kafka.AllocateInventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    @KafkaListener(id = "myListener", topics = "${application.consumer.inventory.topic}", containerGroup = "${application.consumer.inventory.consumer-group}",
            containerFactory = "inventoryConsumerContainer")
    public void listen(ConsumerRecord<String, AllocateInventoryEvent> record) {
        log.info("Received(key={}, partition={}): {}", record.key(), record.partition(), record.value());
    }

}
