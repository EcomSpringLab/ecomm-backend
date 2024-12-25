package dev.shubham.labs.ecomm.kafka.xconsumer.event;

public enum EventStatus {
    RECEIVED,
    PROCESSING,
    CONSUMED_SUCCESSFULLY,
    CONSUMED_FAILURE
}