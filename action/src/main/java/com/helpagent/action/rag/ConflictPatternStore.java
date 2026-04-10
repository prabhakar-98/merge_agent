package com.helpagent.action.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.helpagent.action.config.RagConfig;
import com.helpagent.action.model.dto.ConflictPattern;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG-based conflict pattern store.
 *
 * <p>Stores successful merge conflict resolution patterns as embeddings in an
 * in-memory vector store. When a new conflict is encountered, the agent can
 * query this store for similar past resolutions to guide its approach.
 *
 * <p>Patterns are persisted to disk as JSON for durability across restarts.
 * On startup, existing patterns are loaded and re-embedded.
 */
@Component
public class ConflictPatternStore {

    private static final Logger log = LoggerFactory.getLogger(ConflictPatternStore.class);
    private static final String PATTERNS_FILE = "patterns.json";

    private final RagConfig config;
    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, ConflictPattern> patternIndex; // id -> pattern
    private final ObjectMapper objectMapper;

    public ConflictPatternStore(RagConfig config) {
        this.config = config;
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.patternIndex = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Loads existing patterns from disk on startup.
     */
    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("RAG conflict pattern store is disabled");
            return;
        }

        try {
            Path storageDir = Path.of(config.getStoragePath());
            Files.createDirectories(storageDir);

            Path patternsFile = storageDir.resolve(PATTERNS_FILE);
            if (Files.exists(patternsFile)) {
                List<ConflictPattern> patterns = objectMapper.readValue(
                        patternsFile.toFile(),
                        new TypeReference<List<ConflictPattern>>() {});

                for (ConflictPattern pattern : patterns) {
                    indexPattern(pattern);
                }
                log.info("RAG: Loaded {} conflict patterns from disk", patterns.size());
            } else {
                log.info("RAG: No existing patterns found at {}", patternsFile);
            }
        } catch (Exception e) {
            log.error("RAG: Failed to load patterns from disk: {}", e.getMessage(), e);
        }
    }

    /**
     * Stores a successful conflict resolution pattern.
     * The pattern is embedded, indexed, and persisted to disk.
     *
     * @param pattern the resolved conflict pattern to store
     */
    public void storePattern(ConflictPattern pattern) {
        if (!config.isEnabled()) {
            log.debug("RAG disabled, skipping pattern storage");
            return;
        }

        log.info("RAG: Storing conflict pattern: {}", pattern);

        indexPattern(pattern);
        persistPatterns();

        log.info("RAG: Pattern stored successfully (total patterns: {})", patternIndex.size());
    }

    /**
     * Finds conflict patterns similar to the given description.
     *
     * @param conflictDescription natural-language description of the conflict
     * @param maxResults          maximum number of results (overrides config default)
     * @return list of similar patterns, ordered by similarity (most similar first)
     */
    public List<ConflictPattern> findSimilarPatterns(String conflictDescription, int maxResults) {
        if (!config.isEnabled() || patternIndex.isEmpty()) {
            log.debug("RAG disabled or no patterns stored");
            return List.of();
        }

        int limit = maxResults > 0 ? maxResults : config.getMaxResults();
        log.info("RAG: Searching for patterns similar to: '{}'", truncate(conflictDescription, 80));

        Embedding queryEmbedding = embeddingModel.embed(conflictDescription).content();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                queryEmbedding, limit, config.getSimilarityThreshold());

        List<ConflictPattern> results = matches.stream()
                .map(match -> {
                    String patternId = match.embedded().metadata("patternId");
                    return patternIndex.get(patternId);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("RAG: Found {} similar patterns (threshold: {})", results.size(), config.getSimilarityThreshold());
        return results;
    }

    /**
     * Finds similar patterns using default max results from config.
     */
    public List<ConflictPattern> findSimilarPatterns(String conflictDescription) {
        return findSimilarPatterns(conflictDescription, config.getMaxResults());
    }

    /**
     * Returns the total number of stored patterns.
     */
    public int getPatternCount() {
        return patternIndex.size();
    }

    /**
     * Embeds a pattern and adds it to the in-memory store and index.
     */
    private void indexPattern(ConflictPattern pattern) {
        String embeddingText = pattern.toEmbeddingText();
        Embedding embedding = embeddingModel.embed(embeddingText).content();

        TextSegment segment = TextSegment.from(
                embeddingText,
                dev.langchain4j.data.document.Metadata.from("patternId", pattern.getId())
        );

        embeddingStore.add(embedding, segment);
        patternIndex.put(pattern.getId(), pattern);
    }

    /**
     * Persists all patterns to disk as JSON.
     */
    private void persistPatterns() {
        try {
            Path storageDir = Path.of(config.getStoragePath());
            Files.createDirectories(storageDir);

            Path patternsFile = storageDir.resolve(PATTERNS_FILE);
            List<ConflictPattern> allPatterns = new ArrayList<>(patternIndex.values());

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(patternsFile.toFile(), allPatterns);

            log.debug("RAG: Persisted {} patterns to {}", allPatterns.size(), patternsFile);
        } catch (IOException e) {
            log.error("RAG: Failed to persist patterns: {}", e.getMessage(), e);
        }
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
