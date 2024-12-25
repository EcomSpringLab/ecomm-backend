package dev.shubham.labs.kafka.xconsumer.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {
    @Query("SELECT e FROM EventEntity e WHERE e.topic = ?1 AND e.partition = ?2 AND e.offset = ?3")
    Optional<EventEntity> findByTopicAndPartitionAndOffset(String topic, Integer partition, Long offset);
}