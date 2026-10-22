package com.rplhub.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Thin wrapper over Groq's OpenAI-compatible chat API. Unavailable when app.ai.enabled is false or GROQ_API_KEY is not set. */
@Component
public class AiClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    AiClient(ObjectMapper mapper, @Value("${app.ai.enabled}") boolean enabled, @Value("${app.groq.api-key:}") String apiKey,
             @Value("${app.groq.model}") String model, @Value("${app.groq.base-url}") String baseUrl) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    /** The application.yml switch (app.ai.enabled); exposed to the frontend. */
    public boolean enabled() {
        return enabled;
    }

    public boolean available() {
        return enabled && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** Returns the assistant message text. Throws on API errors so callers (Temporal activities) can retry. */
    public Optional<String> complete(String system, String user, long maxTokens) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user))));
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Groq API error " + response.statusCode() + ": " + response.body());
            }
            JsonNode content = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            return content.isTextual() ? Optional.of(content.asText()) : Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Groq API call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Groq API call interrupted", e);
        }
    }
}
