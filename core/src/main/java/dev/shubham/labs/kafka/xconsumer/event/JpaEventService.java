package dev.shubham.labs.kafka.xconsumer.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Slf4j
@RequiredArgsConstructor
public class JpaEventService<K, V> implements EventService<K, V> {
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveEvent(ConsumerRecord<K, V> record) {
        try {
            EventEntity event = createEventEntity(record);
            eventRepository.save(event);
            log.debug("Saved event for topic: {}, partition: {}, offset: {}",
                    record.topic(), record.partition(), record.offset());
        } catch (Exception e) {
            log.error("Error saving event", e);
            throw new RuntimeException("Failed to save event", e);
        }
    }

    @Override
    @Transactional
    public void updateEventStateToProcessing(ConsumerRecord<K, V> record) {
        updateEventState(record, EventStatus.PROCESSING, null);
    }

    @Override
    @Transactional
    public void updateEventStateToConsumedSuccessfully(ConsumerRecord<K, V> record) {
        updateEventState(record, EventStatus.CONSUMED_SUCCESSFULLY, null);
    }

    @Override
    @Transactional
    public void updateEventStateToConsumedFailure(ConsumerRecord<K, V> record, Throwable error) {
        updateEventState(record, EventStatus.CONSUMED_FAILURE, error.getMessage());
    }

    @Override
    public boolean isNoOp() {
        return false;
    }

    private EventEntity createEventEntity(ConsumerRecord<K, V> record) throws Exception {
        return EventEntity.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .key(serializeKey(record.key()))
                .value(serializeValue(record.value()))
                .status(EventStatus.RECEIVED)
                .build();
    }

    private void updateEventState(ConsumerRecord<K, V> record, EventStatus status, String errorMessage) {
        try {
            EventEntity event = eventRepository
                    .findByTopicAndPartitionAndOffset(record.topic(), record.partition(), record.offset())
                    .orElseThrow(() -> new RuntimeException("Event not found"));

            event.setStatus(status);
            event.setErrorMessage(errorMessage);
            eventRepository.save(event);

            log.debug("Updated event status to {} for topic: {}, partition: {}, offset: {}",
                    status, record.topic(), record.partition(), record.offset());
        } catch (Exception e) {
            log.error("Error updating event state", e);
            throw new RuntimeException("Failed to update event state", e);
        }
    }

    private String serializeKey(K key) throws Exception {
        return key != null ? objectMapper.writeValueAsString(key) : null;
    }

    private String serializeValue(V value) throws Exception {
        return value != null ? objectMapper.writeValueAsString(value) : null;
    }
}
