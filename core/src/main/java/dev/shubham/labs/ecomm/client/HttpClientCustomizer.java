package dev.shubham.labs.ecomm.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

import static dev.shubham.labs.ecomm.util.Helper.uuid;

@Getter
@Slf4j
public class HttpClientCustomizer<S> {

    protected final S client;

    @Builder
    protected HttpClientCustomizer(RestClient.Builder builder, ClientConfig clientConfig, Class<S> serviceType, Retry retry, CircuitBreaker circuitBreaker) {
        Objects.requireNonNull(builder, "RestClient.Builder must not be null");
        Objects.requireNonNull(clientConfig, "ClientConfig must not be null");
        Objects.requireNonNull(serviceType, "Service type must not be null");
        var restClient = builder.clone()
                .baseUrl(clientConfig.getBaseUrl())
                .requestInitializer(clientHttpRequestInitializer())
                .requestInterceptor(new ClientHttpRequestInterceptorCustomizer(retry, circuitBreaker))
                .requestFactory(clientHttpRequestFactory(clientConfig.getConnectTimeout()))
                .build();
        var adapter = RestClientAdapter.create(restClient);
        var factory = HttpServiceProxyFactory.builderFor(adapter).build();
        client = factory.createClient(serviceType);
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(Duration connectTimeout) {
        // Configure java.net.http.HttpClient with HTTP/2 and keep-alive
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(connectTimeout)  // Connection timeout
                .build();

        // Wrap HttpClient in a ClientHttpRequestFactory
        return new JdkClientHttpRequestFactory(httpClient);
    }

    private ClientHttpRequestInitializer clientHttpRequestInitializer() {
        return request -> {
            // Add custom headers
            HttpHeaders headers = request.getHeaders();
            headers.add("x-correlation-id", uuid.get()); // Use existing or generate correlation-id
            // Add idempotent-key for POST requests
            if (HttpMethod.POST.equals(request.getMethod())) {
                headers.add("x-idempotent-key", uuid.get());
            }
        };
    }

}
