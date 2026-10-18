package com.rplhub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Thin wrapper over the Anthropic client. Empty/unavailable when ANTHROPIC_API_KEY is not set. */
@Component
public class AiClient {

    private final AnthropicClient client;
    private final String model;

    AiClient(@Value("${app.anthropic.api-key:}") String apiKey, @Value("${app.anthropic.model}") String model) {
        this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    public boolean available() {
        return client != null;
    }

    public String model() {
        return model;
    }

    /** Returns the text of the first text block. Throws on API errors so callers (Temporal activities) can retry. */
    public Optional<String> complete(String system, String user, long maxTokens) {
        var response = client.messages().create(MessageCreateParams.builder()
            .model(model).maxTokens(maxTokens).system(system).addUserMessage(user).build());
        return response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst();
    }
}
