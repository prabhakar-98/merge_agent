package com.helpagent.action.model.strategy;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Claude;

/**
 * Strategy implementation for Anthropic Claude models.
 * 
 * Uses the ADK's Claude wrapper which integrates with Anthropic's Java SDK.
 * Requires an Anthropic API key configured via environment variable or properties.
 * 
 * Supported models include:
 * - claude-3-7-sonnet-latest (default, balanced)
 * - claude-3-5-sonnet-20241022 (powerful, cost-effective)
 * - claude-3-opus-20240229 (most capable)
 * - claude-3-haiku-20240307 (fastest)
 * 
 * @see <a href="https://google.github.io/adk-docs/agents/models/anthropic/">ADK Anthropic Docs</a>
 */

public class ClaudeModelStrategy extends AbstractModelStrategy {

    private static final String PROVIDER_NAME = "claude";
    private static final String API_KEY_ENV_VAR = "ANTHROPIC_API_KEY";

    private final String apiKey;

    public ClaudeModelStrategy(String modelId, String apiKey) {
        super(modelId);
        this.apiKey = resolveApiKey(apiKey);
    }

    @Override
    public BaseLlm createModel() {
        validateConfiguration();
        log.info("Creating Claude model: {}", modelId);

        // Build the Anthropic client using OkHttp
        AnthropicClient anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        // Create the Claude model wrapper for ADK
        return new Claude(modelId, anthropicClient);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void validateConfiguration() {
        requireNonBlank(modelId, "Model ID");
        requireNonBlank(apiKey, "API Key (set ANTHROPIC_API_KEY environment variable or agent.model.claude.api-key)");
    }

    /**
     * Resolves the API key from provided value or environment variable.
     */
    private String resolveApiKey(String providedKey) {
        if (!isBlank(providedKey)) {
            return providedKey;
        }
        return System.getenv(API_KEY_ENV_VAR);
    }
}
