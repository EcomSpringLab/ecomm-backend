package dev.shubham.labs.ecomm.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Collection;

@HttpExchange(url = "/inventory", accept = "application/json", contentType = "application/json")
public interface InventoryRestClient {

    @GetExchange
    ResponseEntity<String> findInventory(@RequestParam("productId") Collection<String> productId);

}