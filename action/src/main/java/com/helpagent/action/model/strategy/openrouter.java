package com.helpagent.action.model.strategy;

import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class openrouter extends AbstractModelStrategy {

    private static final String PROVIDER_NAME = "openrouter";
    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private final String apiKey;

    public openrouter(String modelId, String apiKey) {
        super(modelId);
        this.apiKey = resolveApiKey(apiKey);
    }

    @Override
    public BaseLlm createModel() {
        validateConfiguration();
        log.info("Creating OpenRouter model: {}", modelId);
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(apiKey)
                .modelName(modelId)
                .build();
        return new OpenAiLlmAdapter(model);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "OpenRouter API key not configured. Set OPENROUTER_API_KEY env variable or agent.model.openrouter.api-key in config.");
        }
    }

    private String resolveApiKey(String configuredKey) {
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            return configuredKey;
        }
        String envKey = System.getenv("OPENROUTER_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey;
        }
        return null;
    }
}
