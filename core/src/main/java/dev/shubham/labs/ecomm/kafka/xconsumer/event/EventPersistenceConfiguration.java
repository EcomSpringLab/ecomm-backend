package dev.shubham.labs.ecomm.kafka.xconsumer.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

//@Configuration
////@ConditionalOnProperty(prefix = "kafka.event.persistence", name = "enabled", havingValue = "true")
//@EnableJpaRepositories(basePackages = {
//        "dev.shubham.labs.ecomm.kafka.xconsumer.event",
//        "dev.shubham.labs.ecomm.kafka.xconsumer.event.repository"
//})
//@EntityScan(basePackages = {
//        "dev.shubham.labs.ecomm.kafka.xconsumer.event",
//        "dev.shubham.labs.ecomm.kafka.xconsumer.event.entity"
//})
public class EventPersistenceConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public <K, V> EventService<K, V> eventService(
            EventRepository eventRepository,
            ObjectMapper objectMapper
    ) {
        return new JpaEventService<>(eventRepository, objectMapper);
    }
}