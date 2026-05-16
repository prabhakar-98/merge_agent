package com.helpagent.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AI model settings.
 * 
 * Supports multiple model providers with their specific configurations.
 * Properties are bound from application.yml under the "agent.model" prefix.
 */
@Component
@ConfigurationProperties(prefix = "agent.model")
public class ModelProperties {

    /**
     * The model provider to use: gemini, claude, ollama, litellm
     */
    private String provider = "gemini";

    /**
     * Gemini-specific configuration
     */
    private GeminiConfig gemini = new GeminiConfig();

    /**
     * Claude/Anthropic-specific configuration
     */
    private ClaudeConfig claude = new ClaudeConfig();

    /**
     * Ollama-specific configuration (for local models)
     */
    private OllamaConfig ollama = new OllamaConfig();

    /**
     * OpenRouter-specific configuration
     */
    private OpenRouterConfig openrouter = new OpenRouterConfig();

    /**
     * LiteLLM-specific configuration
     */
    private LiteLlmConfig litellm = new LiteLlmConfig();

    // Getters and setters
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public GeminiConfig getGemini() {
        return gemini;
    }

    public void setGemini(GeminiConfig gemini) {
        this.gemini = gemini;
    }

    public ClaudeConfig getClaude() {
        return claude;
    }

    public void setClaude(ClaudeConfig claude) {
        this.claude = claude;
    }

    public OllamaConfig getOllama() {
        return ollama;
    }

    public void setOllama(OllamaConfig ollama) {
        this.ollama = ollama;
    }

    public OpenRouterConfig getOpenrouter() {
        return openrouter;
    }

    public void setOpenrouter(OpenRouterConfig openrouter) {
        this.openrouter = openrouter;
    }

    public LiteLlmConfig getLitellm() {
        return litellm;
    }

    public void setLitellm(LiteLlmConfig litellm) {
        this.litellm = litellm;
    }

    /**
     * Gemini model configuration
     */
    public static class GeminiConfig {
        private String modelId = "gemini-2.0-flash-exp";
        private String apiKey;

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    /**
     * Claude/Anthropic model configuration
     * @see <a href="https://google.github.io/adk-docs/agents/models/anthropic/">ADK Anthropic Docs</a>
     */
    public static class ClaudeConfig {
        private String modelId = "claude-opus-4-20250514";
        private String apiKey="";

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    /**
     * Ollama configuration for local models
     */
    public static class OllamaConfig {
        private String modelId = "llama3.2";
        private String baseUrl = "http://localhost:11434";

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class OpenRouterConfig {
        private String modelId = "nvidia/llama-3.3-nemotron-super-49b-v1:free";
        private String apiKey;
        private String baseUrl = "https://openrouter.ai/api/v1";

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    /**
     * LiteLLM configuration for unified API access
     */
    public static class LiteLlmConfig {
        private String modelId;
        private String baseUrl = "http://localhost:4000";
        private String apiKey;

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
