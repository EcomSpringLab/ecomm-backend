package dev.shubham.labs.ecomm.kafka.consumer;

import dev.shubham.labs.ecomm.kafka.KafkaConsumerProps;
import dev.shubham.labs.ecomm.kafka.Record;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class BaseKafkaConsumer<K, V extends Record<K>> {

    private final KafkaConsumerTracer tracer;
    private final CircuitBreaker circuitBreaker;
    private final MessageProcessor<K, V> messageProcessor;
    private final ConcurrentMessageListenerContainer<K, V> container;

    protected BaseKafkaConsumer(KafkaConsumerProps kafkaProperties,
                                OpenTelemetry openTelemetry,
                                MeterRegistry meterRegistry,
                                CircuitBreaker circuitBreaker,
                                MessageProcessor<K, V> messageProcessor,
                                Class<?> key, Class<?> value) {
        this.tracer = new KafkaConsumerTracer(kafkaProperties.getConsumerName(), openTelemetry,circuitBreaker);
        this.circuitBreaker = circuitBreaker;
        this.messageProcessor = messageProcessor;
        this.container = createContainer(kafkaProperties,meterRegistry, key, value);
        setupCircuitBreaker();
    }

    private ConcurrentMessageListenerContainer<K, V> createContainer(KafkaConsumerProps config,MeterRegistry meterRegistry, Class<?> key, Class<?> value) {

        ContainerProperties props = new ContainerProperties(config.getTopic());
        props.setMessageListener(createMessageListener());
        props.setObservationEnabled(true);

        var cf = new DefaultKafkaConsumerFactory<>(consumerConfigs(config, key, value));
        cf.addListener(new MicrometerConsumerListener<>(meterRegistry));
        ConcurrentMessageListenerContainer<K, V> container =
                new ConcurrentMessageListenerContainer<>(cf, props);
        container.setConcurrency(config.getConcurrency());
        return container;
    }

    public Map<String, Object> consumerConfigs(KafkaConsumerProps kafkaProperties, Class<?> key, Class<?> value) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, key);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, value);
        // Error handling deserializer configuration
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return props;
    }

    private MessageListener<K, V> createMessageListener() {
        return record -> tracer.tracedOperation(record,
                () -> messageProcessor.process(record.key(), record.value()));
    }

    private void setupCircuitBreaker() {
//        new CircuitBreakerManager(circuitBreaker, container, tracer).setup();
    }

    public void start() { container.start(); }
    public void stop() { container.stop(); }
}