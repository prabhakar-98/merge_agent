package com.helpagent.action.model.strategy;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;

/**
 * Strategy implementation for Google Gemini models.
 * 
 * Uses the ADK's native Gemini integration which connects directly
 * to Google's Gemini API using the GEMINI_API_KEY environment variable.
 * 
 * Supported models include:
 * - gemini-2.5-flash (default, fast and capable)
 * - gemini-2.5-pro (advanced reasoning)
 * - gemini-2.0-flash-exp (experimental)
 * 
 * @see <a href="https://google.github.io/adk-docs/agents/models/google-gemini/">ADK Gemini Docs</a>
 */
public class GeminiModelStrategy extends AbstractModelStrategy {

    private static final String PROVIDER_NAME = "gemini";
    private static final String API_KEY_ENV_VAR = "GEMINI_API_KEY";

    private final String apiKey;

    public GeminiModelStrategy(String modelId, String apiKey) {
        super(modelId);
        this.apiKey = resolveApiKey(apiKey);
    }

    @Override
    public BaseLlm createModel() {
        validateConfiguration();
        log.info("Creating Gemini model: {}", modelId);
        
        // The Gemini class in ADK requires model ID and API key
        return new Gemini(modelId, apiKey);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void validateConfiguration() {
//        super.validateConfiguration();
//        if (apiKey == null || apiKey.trim().isEmpty()) {
//            throw new IllegalStateException(
//                    String.format("Gemini API key not configured. Set %s environment variable or agent.model.gemini.api-key property",
//                            API_KEY_ENV_VAR));
//        }
    }

    private String resolveApiKey(String configuredKey) {
        // First try the configured key
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            return configuredKey;
        }
        // Fall back to environment variable
        return System.getenv(API_KEY_ENV_VAR);
    }
}
