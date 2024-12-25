package dev.shubham.labs.ecomm.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
class BackpressureHandler<T> {
    private final int maxConcurrency;
    private final Consumer<T> messageProcessor;
    private final BlockingQueue<ConsumerRecord<String, T>> messageQueue;
    private final ExecutorService processingExecutor;
    private final List<CompletableFuture<Void>> workerFutures;
    private volatile boolean isRunning = false;

    public BackpressureHandler(
            int maxConcurrency,
            int queueSize,
            Consumer<T> messageProcessor
    ) {
        this.maxConcurrency = maxConcurrency;
        this.messageProcessor = messageProcessor;
        this.messageQueue = new LinkedBlockingQueue<>(queueSize);
        this.processingExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.workerFutures = new ArrayList<>(maxConcurrency);
    }

    public void start() {
        isRunning = true;
        // Start worker threads (one per concurrency level)
        for (int i = 0; i < maxConcurrency; i++) {
            CompletableFuture<Void> workerFuture = CompletableFuture.runAsync(
                    this::processMessages,
                    processingExecutor
            );
            workerFutures.add(workerFuture);
        }
    }

    public void stop() {
        isRunning = false;
        processingExecutor.shutdown();
        try {
            // Wait for ongoing processing to complete
            if (!processingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processingExecutor.shutdownNow();
        }
    }

    public void handle(ConsumerRecord<String, T> record) {
        try {
            // If queue is full, this will block until space is available
            messageQueue.put(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to queue message", e);
        }
    }

    private void processMessages() {
        while (isRunning) {
            try {
                // Take message from queue (blocks if queue is empty)
                ConsumerRecord<String, T> record = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                if (record != null) {
                    processMessageWithRetry(record);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processMessageWithRetry(ConsumerRecord<String, T> record) {
        try {
            messageProcessor.accept(record.value());
        } catch (Exception e) {
            log.error("Failed to process message from topic: {}, partition: {}, offset: {}",
                    record.topic(), record.partition(), record.offset(), e);
            // Here you could add retry logic or send to DLQ
        }
    }
}