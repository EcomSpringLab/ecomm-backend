package dev.shubham.labs.ecomm.kafka.consumer;

import dev.shubham.labs.ecomm.kafka.KafkaConsumerProps;
import dev.shubham.labs.ecomm.kafka.Record;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Getter
@Slf4j
public abstract class KafkaConsumerConfig<K, V extends Record<K>> {

    protected KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> kafkaListenerContainerFactory;

    protected KafkaConsumerConfig(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value,
                                  MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        kafkaListenerContainerFactory = kafkaListenerContainerFactory(kafkaProperties, key, value, meterRegistry, circuitBreakerRegistry);

    }

    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> kafkaListenerContainerFactory(
            KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value, MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        ConcurrentKafkaListenerContainerFactory<K, V> factory = new ConcurrentKafkaListenerContainerFactory<>();
        var cf = consumerFactory(kafkaProperties, key, value);
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        factory.setConsumerFactory(cf);
//        factory.setRecordInterceptor(new KafkaConsumerInterceptor<>());
        factory.setConcurrency(kafkaProperties.getConcurrency());
        factory.getContainerProperties().setMicrometerEnabled(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setPollTimeout(3000);

        attachCircuitBreakerListener(factory, kafkaProperties, circuitBreakerRegistry);
        return factory;
    }

    public ConsumerFactory<K, V> consumerFactory(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value) {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(kafkaProperties, key, value));
    }

    public Map<String, Object> consumerConfigs(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, key);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, value);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return props;
    }

    private void attachCircuitBreakerListener(ConcurrentKafkaListenerContainerFactory<K, V> factory,
                                              KafkaConsumerProps kafkaProperties,
                                              CircuitBreakerRegistry circuitBreakerRegistry) {
        List<String> cbNames = kafkaProperties.getCircuitBreakers();
        if (cbNames != null) {
            cbNames.forEach(cbName -> {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(cbName);
                circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> handleStateTransition(event, factory));
            });
        }
    }

    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event,
                                       ConcurrentKafkaListenerContainerFactory<K, V> factory) {
        boolean isOpen = event.getStateTransition().getToState().equals(CircuitBreaker.State.OPEN);
        factory.setContainerCustomizer(container -> {
            String topics = String.join(", ", container.getContainerProperties().getTopics());
            String groupId = container.getContainerProperties().getGroupId();
            if (isOpen) {
                log.warn("Circuit breaker is OPEN. Pausing Kafka consumer for topics: [{}], group: [{}], container: [{}]",
                        topics, groupId, container.getListenerId());
                container.pause();
            } else {
                log.info("Circuit breaker is CLOSED. Resuming Kafka consumer for topics: [{}], group: [{}], container: [{}]",
                        topics, groupId, container.getListenerId());
                container.resume();
            }
        });
    }

}
