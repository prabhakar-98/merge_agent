package com.helpagent.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the RAG (Retrieval-Augmented Generation)
 * conflict pattern store.
 *
 * <p>Maps to the {@code rag.*} namespace in application.yml.
 * Controls how conflict resolution patterns are stored and retrieved
 * for future conflict resolution assistance.
 */
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    /** Whether RAG-based pattern lookup is enabled. */
    private boolean enabled = true;

    /** Directory path where conflict patterns are persisted as JSON. */
    private String storagePath = "./data/conflict-patterns";

    /** Maximum number of similar patterns to return per query. */
    private int maxResults = 5;

    /** Minimum cosine similarity threshold for a pattern to be considered relevant. */
    private double similarityThreshold = 0.7;

    // ── Getters & Setters ──

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
}
