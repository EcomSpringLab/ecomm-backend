package dev.shubham.labs.kafka.xconsumer;

import dev.shubham.labs.kafka.KafkaConsumerProps;
import dev.shubham.labs.kafka.xconsumer.event.EventService;
import dev.shubham.labs.kafka.xconsumer.event.NoOpEventService;
import dev.shubham.labs.kafka.xconsumer.lifecycle.ContainerLifecycleStrategy;
import dev.shubham.labs.kafka.xconsumer.lifecycle.NoOpContainerStrategy;
import dev.shubham.labs.kafka.xconsumer.resiliency.NoOpResilienceStrategy;
import dev.shubham.labs.kafka.xconsumer.resiliency.ResilienceStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class KafkaConsumerBuilder<K, V> {


    private KafkaConsumerProps config;
    private Class<?> keyClass;
    private Class<?> valueClass;
    private MeterRegistry meterRegistry;
    private EventService<K, V> eventService = new NoOpEventService<>();
    private ResilienceStrategy resilienceStrategy = new NoOpResilienceStrategy();
    private ContainerLifecycleStrategy containerStrategy = new NoOpContainerStrategy();
    private Consumer<ConsumerRecord<K, V>> messageProcessor;
    private Map<String, Object> additionalProperties = new HashMap<>();

    public KafkaConsumerBuilder<K, V> withConfig(KafkaConsumerProps config) {
        this.config = config;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withKeyClass(Class<?> keyClass) {
        this.keyClass = keyClass;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withValueClass(Class<?> valueClass) {
        this.valueClass = valueClass;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withEventService(EventService<K, V> eventService) {
        this.eventService = eventService;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withResilienceStrategy(ResilienceStrategy resilienceStrategy) {
        this.resilienceStrategy = resilienceStrategy;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withContainerStrategy(ContainerLifecycleStrategy containerStrategy) {
        this.containerStrategy = containerStrategy;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withMessageProcessor(Consumer<ConsumerRecord<K, V>> messageProcessor) {
        this.messageProcessor = messageProcessor;
        return this;
    }

    public KafkaConsumerBuilder<K, V> withAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
        return this;
    }

    public ConcurrentMessageListenerContainer<K, V> build() {
        validateConfiguration();

        ContainerProperties props = createContainerProperties();
        var consumerFactory = createConsumerFactory();

        ConcurrentMessageListenerContainer<K, V> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, props);

        configureContainer(container);
        return container;
    }

    private void validateConfiguration() {
        if (config == null) throw new IllegalStateException("KafkaConsumerProps is required");
        if (keyClass == null) throw new IllegalStateException("Key class is required");
        if (valueClass == null) throw new IllegalStateException("Value class is required");
        if (messageProcessor == null) throw new IllegalStateException("Message processor is required");
    }

    private ContainerProperties createContainerProperties() {
        ContainerProperties props = new ContainerProperties(config.getTopic());
        props.setAckMode(ContainerProperties.AckMode.MANUAL);
        props.setMessageListener(
                new CustomizedMessageListener<>(eventService, resilienceStrategy, messageProcessor)
                        .messageListener()
        );
        props.setObservationEnabled(true);
        return props;
    }

    private ConsumerFactory<K, V> createConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyClass);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueClass);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, ErrorHandlingDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putAll(additionalProperties);

        ConsumerFactory<K, V> factory = new DefaultKafkaConsumerFactory<>(props);
        if (meterRegistry != null) {
            factory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        }
        return factory;
    }

    private void configureContainer(ConcurrentMessageListenerContainer<K, V> container) {
        container.setConcurrency(config.getConcurrency());

        containerStrategy.registerContainer(container);
    }
}
