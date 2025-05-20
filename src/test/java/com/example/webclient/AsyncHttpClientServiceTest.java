// File: src/test/java/com/example/webclient/AsyncHttpClientServiceTest.java
package com.example.webclient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpMethod;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AsyncHttpClientServiceTest {

    private static WireMockServer wireMockServer;
    private AsyncHttpClientService service;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);
        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/mock-endpoint"))
                .willReturn(WireMock.aResponse().withStatus(500).withBody("Internal Server Error")));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    public void setup() {
        WebClient.Builder webClientBuilder = WebClient.builder().baseUrl("http://localhost:8080");
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();



        // Use custom RetryConfig with maxAttempts set to 5
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(500))
                .build();
        retryRegistry = RetryRegistry.of(retryConfig);

        // Configure the CircuitBreaker and Retry with default settings
        //retryRegistry = RetryRegistry.ofDefaults();
        service = new AsyncHttpClientService(
                webClientBuilder,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                circuitBreakerRegistry,
                retryRegistry
        );
    }

    @Test
    public void testCircuitBreakerAndRetry() {
        // Subscribe to circuit breaker events
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("asyncClient");
        List<CircuitBreakerEvent> events = new ArrayList<>();
        circuitBreaker.getEventPublisher()
                .onEvent(events::add);

        RequestHolder<String> request = new RequestHolder<>(HttpMethod.GET, "/mock-endpoint", String.class);
        StepVerifier.create(service.fetchRawAsync(request, null))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage() != null &&
                        throwable.getMessage().contains("5xx"))
                .verify();

        // After execution, print or assert against the events list.
        // For example, you could check the number of error or state transition events.
        System.out.println("CircuitBreaker Events: " + events);

        // You might then assert that the number of failures or state changes meets your fallback criteria.
        Assertions.assertFalse(events.isEmpty(), "Expected some circuit breaker events");
    }

    @Test
    public void testCircuitBreakerAndRetryWithPrint() {
        // Subscribe to circuit breaker events
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("asyncClient");
        List<CircuitBreakerEvent> cbEvents = new ArrayList<>();
        circuitBreaker.getEventPublisher().onEvent(cbEvents::add);

        // Subscribe to retry events
        Retry retry = retryRegistry.retry("asyncClient");
        List<io.github.resilience4j.retry.event.RetryEvent> retryEvents = new ArrayList<>();
        retry.getEventPublisher().onEvent(retryEvents::add);

        RequestHolder<String> request = new RequestHolder<>(HttpMethod.GET, "/mock-endpoint", String.class);
        StepVerifier.create(service.fetchRawAsync(request, null))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                        throwable.getMessage() != null &&
                        throwable.getMessage().contains("5xx"))
                .verify();

        // Print counts for debugging
        System.out.println("CircuitBreaker Events: " + cbEvents);
        System.out.println("Total Retry Attempts: " + retryEvents.size());

        // Assert that some retry attempts have been logged
        Assertions.assertFalse(retryEvents.isEmpty(), "Expected some retry events");
    }
}