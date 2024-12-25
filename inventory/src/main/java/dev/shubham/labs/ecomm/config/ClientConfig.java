package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.ecomm.client.ClientProps;
import dev.shubham.labs.ecomm.client.HttpClientCustomizer;
import dev.shubham.labs.ecomm.client.InventoryRestClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ClientConfig {


    @Bean
    @ConfigurationProperties(prefix = "application.client.inventory")
    public ClientProps inventoryClientProps() {
        return new ClientProps();
    }

    @Bean
    public InventoryRestClient inventoryRestClient(RestClient.Builder builder, ClientProps inventoryClientProps, RetryRegistry registry, CircuitBreakerRegistry circuitBreakerRegistry) {
        return HttpClientCustomizer.<InventoryRestClient>builder()
                .builder(builder)
                .clientProps(inventoryClientProps)
                .serviceType(InventoryRestClient.class)
                .retry(registry.retry("backendA"))
                .circuitBreaker(circuitBreakerRegistry.circuitBreaker("backendA"))
                .build().getClient();
    }

}
