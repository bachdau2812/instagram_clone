package com.dauducbach.feed_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor

public class GetVectorEmbedding {
    private final WebClient webClient;

    @Value("${gemini-key}")
    private String apiKey;

    public Mono<List<Double>> getEmbedding(String text) {
        String uri = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-exp-03-07:embedContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "model", "models/gemini-embedding-exp-03-07",
                "content", Map.of(
                        "parts", new Object[]{ Map.of("text", text) }
                )
        );

        return webClient.post()
                .uri(uri)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(response -> {
                    Map<String, Object> embeddingMap = (Map<String, Object>) response.get("embedding");

                    return (List<Double>) embeddingMap.get("values");
                });
    }
}
