package com.helpagent.action.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a stored conflict resolution pattern in the RAG store.
 * Successful resolutions are persisted so the agent can retrieve
 * similar past patterns when encountering new conflicts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConflictPattern {

    /** Unique identifier for this pattern. */
    private String id;

    /** Natural-language description of the conflict type. */
    private String conflictDescription;

    /** File path glob pattern (e.g., "*.java", "pom.xml"). */
    private String filePattern;

    /** Description of how the conflict was resolved. */
    private String resolutionStrategy;

    /** Example resolved file content (may be truncated for large files). */
    private String resolvedContent;

    /** Source repository (owner/repo). */
    private String repository;

    /** When this pattern was stored. */
    private Instant timestamp;

    public ConflictPattern() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public ConflictPattern(String conflictDescription, String filePattern,
                           String resolutionStrategy, String resolvedContent,
                           String repository) {
        this();
        this.conflictDescription = conflictDescription;
        this.filePattern = filePattern;
        this.resolutionStrategy = resolutionStrategy;
        this.resolvedContent = resolvedContent;
        this.repository = repository;
    }

    /**
     * Builds a text representation used for embedding / similarity search.
     * Combines the most semantically meaningful fields.
     */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Conflict: ").append(conflictDescription).append("\n");
        sb.append("File pattern: ").append(filePattern).append("\n");
        sb.append("Resolution: ").append(resolutionStrategy);
        return sb.toString();
    }

    // ── Getters & Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConflictDescription() { return conflictDescription; }
    public void setConflictDescription(String conflictDescription) { this.conflictDescription = conflictDescription; }

    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }

    public String getResolutionStrategy() { return resolutionStrategy; }
    public void setResolutionStrategy(String resolutionStrategy) { this.resolutionStrategy = resolutionStrategy; }

    public String getResolvedContent() { return resolvedContent; }
    public void setResolvedContent(String resolvedContent) { this.resolvedContent = resolvedContent; }

    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return String.format("ConflictPattern{id='%s', conflict='%s', filePattern='%s', repo='%s'}",
                id, truncate(conflictDescription, 60), filePattern, repository);
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
