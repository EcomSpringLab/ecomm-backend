package dev.shubham.labs.kafka.xconsumer.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public class NoOpEventService<K, V> implements EventService<K, V> {
    @Override
    public void saveEvent(ConsumerRecord<K, V> record) {
        // No-op
    }

    @Override
    public void updateEventStateToProcessing(ConsumerRecord<K, V> record) {
        // No-op
    }

    @Override
    public void updateEventStateToConsumedSuccessfully(ConsumerRecord<K, V> record) {
        // No-op
    }

    @Override
    public void updateEventStateToConsumedFailure(ConsumerRecord<K, V> record, Throwable error) {
        // No-op
    }

    @Override
    public boolean isNoOp() {
        return true;
    }
}