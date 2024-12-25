package dev.shubham.labs.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.function.Supplier;

import static dev.shubham.labs.Helper.uuid;

@Slf4j
public class ClientHttpRequestInterceptorCustomizer implements ClientHttpRequestInterceptor {

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ClientHttpRequestInterceptorCustomizer(Retry retry, CircuitBreaker circuitBreaker) {
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        // Supplier to execute the request
        Supplier<ClientHttpResponse> supplier = () -> {
            try {
                // Update the retry count header dynamically
                HttpHeaders headers = request.getHeaders();
                headers.set("x-request-id", uuid.get());
                headers.set("x-retry-count", getRetryCount());
                return execution.execute(request, body);
            } catch (IOException e) {
                throw new RuntimeException(e.getCause());
            }
        };

        // Apply Retry if provided
        if (retry != null) {
            supplier = Retry.decorateSupplier(retry, supplier);
        }

        // Apply CircuitBreaker if provided
        if (circuitBreaker != null) {
            supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        }

        return supplier.get();
    }

    private String getRetryCount() {
        if (retry != null) {
            // Fetch retry metrics from Resilience4j Retry instance
            long currentRetryCount = retry.getMetrics().getNumberOfTotalCalls();
            return String.valueOf(currentRetryCount);
        }
        return "0"; // Default if retry is null
    }
}
