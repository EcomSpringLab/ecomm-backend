package dev.shubham.labs.ecomm.kafka.xconsumer.event;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Event<K, V> {

    private K key;
    private V value;
}
