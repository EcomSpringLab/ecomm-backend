package dev.shubham.labs.ecomm.kafka.consumer;

import dev.shubham.labs.ecomm.kafka.KafkaConsumerProps;
import dev.shubham.labs.ecomm.kafka.Record;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;


@Getter
public abstract class KafkaConsumerConfig<K, V extends Record<K>> {

    protected KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> kafkaListenerContainerFactory;

    protected KafkaConsumerConfig(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value,
                                  MeterRegistry meterRegistry) {
        kafkaListenerContainerFactory = kafkaListenerContainerFactory(kafkaProperties, key, value, meterRegistry);

    }

    KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> kafkaListenerContainerFactory(
            KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value, MeterRegistry meterRegistry) {
        ConcurrentKafkaListenerContainerFactory<K, V> factory = new ConcurrentKafkaListenerContainerFactory<>();
        var cf = consumerFactory(kafkaProperties, key, value);
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        factory.setConsumerFactory(cf);
        factory.setConcurrency(kafkaProperties.getConcurrency());
        factory.getContainerProperties().setMicrometerEnabled(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setPollTimeout(3000);
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

}
