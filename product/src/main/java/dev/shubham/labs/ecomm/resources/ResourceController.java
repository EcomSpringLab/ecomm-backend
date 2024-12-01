package dev.shubham.labs.ecomm.resources;

import dev.shubham.labs.ecomm.client.InventoryRestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ResourceController {

    private final InventoryRestClient inventoryRestClient;

    @GetMapping(path = "/test")
    ResponseEntity<String> status() {
        inventoryRestClient.findInventory(Collections.singleton("test"));
        return ResponseEntity.status(200).body("Success");

    }
}
