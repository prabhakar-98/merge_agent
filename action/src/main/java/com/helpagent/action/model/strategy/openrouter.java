package com.helpagent.action.model.strategy;

import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class openrouter extends AbstractModelStrategy {

    private static final String PROVIDER_NAME = "openrouter";
    private static final String API_KEY = "OPENROUTER_API_KEY";
    private final String apiKey;
    public openrouter(String modelId, String apiKey) {
        super(modelId);
        this.apiKey= resolveApiKey(apiKey);
    }

    @Override
    public BaseLlm createModel() {
        validateConfiguration();
        log.info("Creating OpenRouter model: {}", modelId);
        OpenAiChatModel model = OpenAiChatModel.builder()
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

    }

     private String resolveApiKey(String configuredKey) {
        // First try the configured key
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            return configuredKey;
        }
        // Fall back to environment variable
        String envKey = System.getenv(API_KEY);
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey;
        }
        return null; // No API key found
    }


}
