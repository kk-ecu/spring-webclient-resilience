package com.example.webclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.reactor.circuitbreaker.operator.*;
import io.github.resilience4j.reactor.retry.RetryOperator;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.*;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nullable;
import java.io.*;
import java.time.Duration;
import java.util.UUID;

@Service
public class AsyncHttpClientService {

    private final WebClient webClient;
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

    public <T> Mono<T> fetchAndParseAsync(RequestHolder<T> requestHolder, @Nullable HttpEntity<?> entity) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        long start = System.currentTimeMillis();

        Flux<DataBuffer> dataBufferFlux = webClient
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
            .bodyToFlux(DataBuffer.class);

        return Mono.defer(() -> DataBufferUtils.join(dataBufferFlux))
            .timeout(Duration.ofSeconds(30))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .publishOn(Schedulers.boundedElastic())
            .map(dataBuffer -> {
                try (InputStream inputStream = dataBuffer.asInputStream(true)) {
                    @SuppressWarnings("unchecked")
                    T parsed = (T) objectMapper.readValue(inputStream, requestHolder.getJaxbClass());
                    return parsed;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to parse response", e);
                }
            })
            .doFinally(signal -> MDC.clear());
    }
}
