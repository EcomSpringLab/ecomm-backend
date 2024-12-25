package dev.shubham.labs.kafka.xconsumer.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
public class EventPersistenceAutoConfiguration {

    @Bean
    @Fallback
    public <K, V> EventService<K, V> noOpEventService() {
        return new NoOpEventService<>();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({EventEntity.class, EventRepository.class})
    @EnableJpaRepositories(basePackageClasses = EventRepository.class)
    @EntityScan(basePackageClasses = {EventEntity.class})
    @ConditionalOnProperty(name = "event.storage.jpa.enabled", havingValue = "true")
    static class JpaConfig {

        @Bean
        public <K, V> EventService<K, V> jpaEventService(
                EventRepository eventRepository,
                ObjectMapper objectMapper) {
            return new JpaEventService<>(eventRepository, objectMapper);
        }
    }
}