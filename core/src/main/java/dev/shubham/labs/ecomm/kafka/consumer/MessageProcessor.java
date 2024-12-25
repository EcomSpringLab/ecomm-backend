package dev.shubham.labs.ecomm.kafka.consumer;

public interface MessageProcessor<K, V> {
    void process(K key, V value);
}