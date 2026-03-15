package com.helpagent.action.model.strategy;

import com.google.adk.models.BaseLlm;

/**
 * Strategy interface for AI model providers.
 * 
 * This follows the Strategy Design Pattern to allow flexible switching
 * between different AI model providers (Gemini, Claude, Ollama, etc.)
 * without changing the client code.
 * 
 * @see <a href="https://google.github.io/adk-docs/agents/models/">ADK Models Documentation</a>
 */
public interface ModelStrategy {

    /**
     * Creates and returns a model instance configured for the specific provider.
     * The returned object will be a provider-specific model implementation
     * (e.g., Gemini, Claude, etc.) that extends BaseLlm.
     *
     * @return the configured model instance
     */
    BaseLlm createModel();

    /**
     * Returns the name of the model provider.
     *
     * @return provider name (e.g., "gemini", "claude", "ollama")
     */
    String getProviderName();

    /**
     * Returns the specific model ID being used.
     *
     * @return model ID (e.g., "gemini-2.5-flash", "claude-3-7-sonnet-latest")
     */
    String getModelId();

    /**
     * Validates that the required configuration is present.
     *
     * @throws IllegalStateException if required configuration is missing
     */
    void validateConfiguration();
}
