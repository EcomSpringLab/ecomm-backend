package dev.shubham.labs.kafka.consumer;

public interface MessageProcessor<K, V> {
    void process(K key, V value);
}