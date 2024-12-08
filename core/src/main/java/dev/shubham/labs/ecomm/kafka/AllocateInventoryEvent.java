package dev.shubham.labs.ecomm.kafka;


public record AllocateInventoryEvent(String beerId) implements Record<String> {
    @Override
    public String key() {
        return beerId();
    }
}
