package com.helpagent.action.model.strategy;

import com.helpagent.action.config.ModelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating ModelStrategy instances based on configuration.
 * 
 * This factory encapsulates the logic for selecting and instantiating
 * the appropriate model strategy based on the configured provider.
 * 
 * Usage:
 * <pre>
 * ModelStrategy strategy = modelStrategyFactory.createStrategy();
 * Object model = strategy.createModel();
 * </pre>
 */
@Component
public class ModelStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(ModelStrategyFactory.class);

    private final ModelProperties modelProperties;

    public ModelStrategyFactory(ModelProperties modelProperties) {
        this.modelProperties = modelProperties;
    }

    /**
     * Creates a ModelStrategy based on the configured provider.
     *
     * @return the appropriate ModelStrategy implementation
     * @throws IllegalArgumentException if the provider is not supported
     */
    public ModelStrategy createStrategy() {
        String provider = modelProperties.getProvider().toLowerCase();
        log.info("Creating model strategy for provider: {}", provider);

        return switch (provider) {
            case "gemini" -> createGeminiStrategy();
            case "claude", "anthropic" -> createClaudeStrategy();
            case "openrouter" -> createOpenRouterStrategy();
            default -> throw new IllegalArgumentException(
                "Unsupported model provider: " + provider +
                ". Supported providers: gemini, claude, openrouter, ollama, litellm"
            );
        };
    }

    /**
     * Creates a Gemini model strategy.
     */
    private GeminiModelStrategy createGeminiStrategy() {
        ModelProperties.GeminiConfig config = modelProperties.getGemini();
        return new GeminiModelStrategy(config.getModelId(), config.getApiKey());
    }

    /**
     * Creates a Claude/Anthropic model strategy.
     */
    private ClaudeModelStrategy createClaudeStrategy() {
        ModelProperties.ClaudeConfig config = modelProperties.getClaude();
        return new ClaudeModelStrategy(config.getModelId(), config.getApiKey());
    }

    private openrouter createOpenRouterStrategy() {
        ModelProperties.OpenRouterConfig config = modelProperties.getOpenrouter();
        return new openrouter(config.getModelId(), config.getApiKey());
    }



    /**
     * Gets the currently configured provider name.
     */
    public String getConfiguredProvider() {
        return modelProperties.getProvider();
    }

    /**
     * Checks if a specific provider is configured.
     */
    public boolean isProvider(String provider) {
        return modelProperties.getProvider().equalsIgnoreCase(provider);
    }
}
