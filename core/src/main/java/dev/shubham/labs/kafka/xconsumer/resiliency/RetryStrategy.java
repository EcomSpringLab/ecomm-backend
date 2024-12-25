package dev.shubham.labs.kafka.xconsumer.resiliency;

import io.github.resilience4j.retry.Retry;

public class RetryStrategy implements ResilienceStrategy {
    private final Retry retry;

    public RetryStrategy(Retry retry) {
        this.retry = retry;
    }

    @Override
    public void executeWithResilience(Runnable action) throws Exception {
        retry.executeRunnable(action);
    }
}
