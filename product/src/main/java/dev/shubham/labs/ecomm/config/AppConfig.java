package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.ecomm.client.ClientConfig;
import dev.shubham.labs.ecomm.client.HttpClientCustomizer;
import dev.shubham.labs.ecomm.client.InventoryRestClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AppConfig {


    @Bean
    @ConfigurationProperties(prefix = "application.client.inventory")
    public ClientConfig inventoryClientConfig() {
        return new ClientConfig();
    }

    @Bean
    public InventoryRestClient inventoryRestClient(RestClient.Builder builder, ClientConfig inventoryClientConfig, RetryRegistry registry, CircuitBreakerRegistry circuitBreakerRegistry) {
        return HttpClientCustomizer.<InventoryRestClient>builder()
                .builder(builder)
                .clientConfig(inventoryClientConfig)
                .serviceType(InventoryRestClient.class)
                .retry(registry.retry("backendA"))
                .circuitBreaker(circuitBreakerRegistry.circuitBreaker("backendA"))
                .build().getClient();
    }

}
