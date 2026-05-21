package com.bllose.agent.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaClassifier {

    private static final Logger log = LoggerFactory.getLogger(OllamaClassifier.class);

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public OllamaClassifier(
            @Value("${app.guard.ollama.base-url}") String baseUrl,
            @Value("${app.guard.ollama.model}") String model,
            @Value("${app.guard.ollama.timeout-seconds}") int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 ollama 模型分类。返回 null 表示调用失败（降级放行）。
     */
    public String classify(String message) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "user", "content", message)
                ),
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0, "num_predict", 30)
            );

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Ollama returned status {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("message").path("content").asText();
            JsonNode categoryNode = objectMapper.readTree(content).path("category");
            if (categoryNode.isTextual()) {
                return categoryNode.asText();
            }
            log.warn("Unexpected ollama response: {}", content);
            return null;
        } catch (IOException | InterruptedException e) {
            log.warn("Ollama classification failed, falling back to allow", e);
            return null;
        }
    }
}
