package dev.shubham.labs.kafka.consumer;

import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
public class KafkaHeaderExtractor implements TextMapGetter<ConsumerRecord<?, ?>> {
    @Override
    public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
        return () -> StreamSupport.stream(carrier.headers().spliterator(), false)
                .map(Header::key)
                .iterator();
    }

    @Override
    public String get(ConsumerRecord<?, ?> carrier, String key) {
        Header header = carrier.headers().lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}