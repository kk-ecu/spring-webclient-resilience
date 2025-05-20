package com.example.webclient;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.reactor.circuitbreaker.operator.*;
import io.github.resilience4j.reactor.retry.RetryOperator;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import org.springframework.lang.Nullable;
import java.io.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
public class AsyncHttpClientService {

    @Autowired
    private WebClient webClient;

    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AsyncHttpClientService(WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper,
                                  CircuitBreakerRegistry cbRegistry,
                                  RetryRegistry retryRegistry) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.circuitBreaker = cbRegistry.circuitBreaker("asyncClient");
        this.retry = retryRegistry.retry("asyncClient");
    }

    // Use this for POJO mapping
    public <T> Mono<T> fetchAndParseAsync(RequestHolder<T> requestHolder, @Nullable HttpEntity<?> entity) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        return webClient
                .method(requestHolder.getHttpVerb())
                .uri(requestHolder.getUri())
                .headers(headers -> {
                    if (entity != null) headers.addAll(entity.getHeaders());
                })
                .bodyValue(entity != null && entity.getBody() != null ? entity.getBody() : "")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            return Mono.error(new RuntimeException("4xx error: " + body));
                        })
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            return Mono.error(new RuntimeException("5xx error: " + body));
                        })
                )
                .bodyToMono(requestHolder.getJaxbClass())
                .timeout(Duration.ofSeconds(30))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorResume(throwable -> {
                    // Preserve errors that already contain "5xx" in the message
                    if (throwable instanceof RuntimeException &&
                            throwable.getMessage() != null &&
                            throwable.getMessage().contains("5xx")) {
                        return Mono.error(throwable);
                    } else if (throwable instanceof IOException) {
                        return Mono.error(new RuntimeException("Network error occurred", throwable));
                    } else if (throwable instanceof TimeoutException) {
                        return Mono.error(new RuntimeException("Request timed out", throwable));
                    }
                    return Mono.error(new RuntimeException("Unexpected error occurred", throwable));
                })
                .doFinally(signal -> MDC.clear());
    }

    // Use this for raw JSON string
    public Mono<String> fetchRawAsync(RequestHolder<String> requestHolder, @Nullable HttpEntity<?> entity) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        return webClient
                .method(requestHolder.getHttpVerb())
                .uri(requestHolder.getUri())
                .headers(headers -> {
                    if (entity != null) headers.addAll(entity.getHeaders());
                })
                .bodyValue(entity != null && entity.getBody() != null ? entity.getBody() : "")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            return Mono.error(new RuntimeException("4xx error: " + body));
                        })
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            return Mono.error(new RuntimeException("5xx error: " + body));
                        })
                )
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .onErrorResume(throwable -> {
                    // Preserve errors that already contain "5xx" in the message
                    if (throwable instanceof RuntimeException &&
                            throwable.getMessage() != null &&
                            throwable.getMessage().contains("5xx")) {
                        return Mono.error(throwable);
                    } else if (throwable instanceof IOException) {
                        return Mono.error(new RuntimeException("Network error occurred", throwable));
                    } else if (throwable instanceof TimeoutException) {
                        return Mono.error(new RuntimeException("Request timed out", throwable));
                    }
                    return Mono.error(new RuntimeException("Unexpected error occurred", throwable));
                })
                .doFinally(signal -> MDC.clear());
    }
}