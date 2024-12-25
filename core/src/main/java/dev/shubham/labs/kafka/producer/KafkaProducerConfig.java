package dev.shubham.labs.kafka.producer;

import dev.shubham.labs.kafka.KafkaProducerProps;
import dev.shubham.labs.kafka.Record;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Tracer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerProducerListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Slf4j
@Getter
public abstract class KafkaProducerConfig<K, V extends Record<K>> {

    protected final KafkaTemplate<K, V> kafkaTemplate;

    protected final ObservationRegistry registry;

    protected final ExecutorService kafkaProducerExecutor;

    protected final Tracer tracer;

    KafkaProducerConfig(KafkaProducerProps kafkaProperties, Class<?> keySerializer, Class<?> valueSerializer,
                        MeterRegistry meterRegistry, ObservationRegistry registry, ExecutorService kafkaProducerExecutor, Tracer tracer) {
        this.kafkaProducerExecutor = kafkaProducerExecutor;
        this.registry = registry;
        this.tracer = tracer;
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, kafkaProperties.isIdempotence());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        // Configure the KafkaAvroSerializer.
        // props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        // KafkaAvroSerializer.class.getName());
        //
        // // Schema Registry location.
        // props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
        // "http://localhost:8081");
        var pf = new DefaultKafkaProducerFactory<K, V>(props);
        pf.addListener(new MicrometerProducerListener<>(meterRegistry));
        this.kafkaTemplate = new KafkaTemplate<>(pf);
        this.kafkaTemplate.setDefaultTopic(kafkaProperties.getTopic());
        this.kafkaTemplate.setObservationEnabled(true);
        this.kafkaTemplate.setMicrometerEnabled(true);

    }


}
