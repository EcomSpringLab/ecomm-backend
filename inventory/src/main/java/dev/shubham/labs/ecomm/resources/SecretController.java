package dev.shubham.labs.ecomm.resources;


import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecretController {

    private final Environment environment;

    public SecretController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/secrets/{key}")
    public String getSecretMessage(@PathVariable String key) {
        String value = environment.getProperty(key);
        return StringUtils.hasText(value) ? value : "Secret not found!";
    }
}