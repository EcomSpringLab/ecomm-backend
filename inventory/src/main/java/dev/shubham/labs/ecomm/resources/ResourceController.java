package dev.shubham.labs.ecomm.resources;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ResourceController {

    @GetMapping(path = "/inventory")
    ResponseEntity<String> status(@RequestParam("productId") Collection<String> productId) {
        log.info("request {}", productId);
        return ResponseEntity.status(200).body("Success");

    }
}
