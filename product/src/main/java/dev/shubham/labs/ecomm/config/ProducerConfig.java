package dev.shubham.labs.ecomm.config;

import dev.shubham.labs.kafka.AllocateInventoryEvent;
import dev.shubham.labs.kafka.KafkaProducerConfig;
import dev.shubham.labs.kafka.producer.KafkaProducerService;
import dev.shubham.labs.kafka.producer.KafkaProducerServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.TracingProducerInterceptor;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.OtelResourceProperties;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryProperties;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaProducerConfig.class)
public class ProducerConfig {

    @Bean
    public ExecutorService kafkaProducerExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("kafka-producer-", 0L).factory());
    }

    @Bean
    public KafkaProducerService<String, AllocateInventoryEvent> allocateInventoryProducerService(
            KafkaProducerConfig kafkaProducerConfig, MeterRegistry meterRegistry, ObservationRegistry registry,
            ExecutorService kafkaProducerExecutor, Tracer tracer, DefaultKafkaProducerFactoryCustomizer kafkaProducerMetrics,OpenTelemetry openTelemetry) {
        return new KafkaProducerServiceImpl<>(kafkaProducerConfig.getInstances().get("inventory"), StringSerializer.class, JsonSerializer.class,
                meterRegistry, registry, kafkaProducerExecutor, tracer,kafkaProducerMetrics,openTelemetry) {
        };
    }

//    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProducerConfig kafkaProducerConfig) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProducerConfig.getInstances().get("inventory").getBootstrapServers());
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TracingProducerInterceptor.class.getName());
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        //typical properties
        return new DefaultKafkaProducerFactory<>(configProps);
    }

//    @Bean
    public KafkaTemplate<String, String> kafkaTemplateCusTom(ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> stringStringKafkaTemplate = new KafkaTemplate<>(producerFactory);
        stringStringKafkaTemplate.setDefaultTopic("allocate-inventory-event");

        stringStringKafkaTemplate.setObservationEnabled(true);//trying out with
        return stringStringKafkaTemplate;
    }



}
