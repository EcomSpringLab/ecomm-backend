package dev.shubham.labs.kafka.xconsumer.resiliency;

public interface ResilienceStrategy {
    void executeWithResilience(Runnable action) throws Throwable;

    // Decorator method to chain resilience patterns
    default ResilienceStrategy andThen(ResilienceStrategy next) {
        return (action) -> this.executeWithResilience(
                () -> {
                    try {
                        next.executeWithResilience(action);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}