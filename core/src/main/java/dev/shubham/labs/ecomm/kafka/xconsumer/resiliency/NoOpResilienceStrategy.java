package dev.shubham.labs.ecomm.kafka.xconsumer.resiliency;

public class NoOpResilienceStrategy implements ResilienceStrategy {
    @Override
    public void executeWithResilience(Runnable action) throws Exception {
        action.run();
    }
}
