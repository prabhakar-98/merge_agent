package com.helpagent.action.model.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for model strategies providing common functionality.
 */
public abstract class AbstractModelStrategy implements ModelStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String modelId;

    protected AbstractModelStrategy(String modelId) {
        this.modelId = modelId;
    }

    @Override
    public String getModelId() {
        return modelId;
    }

    /**
     * Helper method to check if a string is blank.
     */
    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Helper method to require a non-blank configuration value.
     */
    protected void requireNonBlank(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                String.format("%s configuration is required for %s provider", name, getProviderName())
            );
        }
    }
}
