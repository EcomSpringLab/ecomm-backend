package dev.shubham.labs.ecomm.kafka.xconsumer.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface EventService<K, V> {

    void saveEvent(ConsumerRecord<K, V> record);

    void updateEventStateToProcessing(ConsumerRecord<K, V> record);

    void updateEventStateToConsumedSuccessfully(ConsumerRecord<K, V> record);

    void updateEventStateToConsumedFailure(ConsumerRecord<K, V> record, Throwable e);

    boolean isNoOp();
}
