package com.helpagent.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.model.FileDiff;
import com.helpagent.action.model.ReviewComment;
import com.helpagent.action.model.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the GitHub REST API.
 * Handles fetching PR data, diffs, files, and posting review comments.
 */
@Service
public class GitHubApiService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GitHubApiService(
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
            @Value("${github.api.token:}") String token,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /**
     * Fetches the list of files changed in a pull request with their diffs.
     */
    public List<FileDiff> getPullRequestFiles(String repoFullName, int prNumber) {
        log.info("Fetching PR files for {}/pull/{}", repoFullName, prNumber);

        List<FileDiff> allFiles = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            String response = webClient.get()
                    .uri("/repos/{repo}/pulls/{pr}/files?per_page={perPage}&page={page}",
                            repoFullName, prNumber, perPage, page)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            try {
                JsonNode files = objectMapper.readTree(response);
                if (!files.isArray() || files.isEmpty()) break;

                for (JsonNode file : files) {
                    FileDiff diff = new FileDiff();
                    diff.setFilename(file.path("filename").asText());
                    diff.setStatus(file.path("status").asText());
                    diff.setAdditions(file.path("additions").asInt());
                    diff.setDeletions(file.path("deletions").asInt());
                    diff.setChanges(file.path("changes").asInt());
                    diff.setPatch(file.path("patch").asText(null));
                    if (file.has("previous_filename")) {
                        diff.setPreviousFilename(file.path("previous_filename").asText());
                    }
                    allFiles.add(diff);
                }

                if (files.size() < perPage) break;
                page++;
            } catch (Exception e) {
                log.error("Failed to parse PR files response", e);
                break;
            }
        }

        log.info("Fetched {} files for {}/pull/{}", allFiles.size(), repoFullName, prNumber);
        return allFiles;
    }

    /**
     * Fetches the raw diff content for a pull request.
     */
    public String getPullRequestDiff(String repoFullName, int prNumber) {
        log.info("Fetching raw diff for {}/pull/{}", repoFullName, prNumber);

        return webClient.get()
                .uri("/repos/{repo}/pulls/{pr}", repoFullName, prNumber)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * Fetches a specific file's content from a repository at a given ref.
     */
    public String getFileContent(String repoFullName, String path, String ref) {
        log.debug("Fetching file content: {}/{} @ {}", repoFullName, path, ref);

        try {
            String response = webClient.get()
                    .uri("/repos/{repo}/contents/{path}?ref={ref}",
                            repoFullName, path, ref)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.raw")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response;
        } catch (Exception e) {
            log.warn("Failed to fetch file content for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches pull request details (title, body, metadata).
     */
    public Map<String, Object> getPullRequestDetails(String repoFullName, int prNumber) {
        log.info("Fetching PR details for {}/pull/{}", repoFullName, prNumber);

        String response = webClient.get()
                .uri("/repos/{repo}/pulls/{pr}", repoFullName, prNumber)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readValue(response,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
        } catch (Exception e) {
            log.error("Failed to parse PR details", e);
            return Map.of();
        }
    }

    /**
     * Posts a review with inline comments on a pull request.
     */
    public void submitReview(String repoFullName, int prNumber, ReviewResult reviewResult) {
        log.info("Submitting review for {}/pull/{} with verdict: {}",
                repoFullName, prNumber, reviewResult.getVerdict());

        Map<String, Object> reviewBody = new HashMap<>();
        reviewBody.put("commit_id", reviewResult.getCommitSha());
        reviewBody.put("body", reviewResult.getSummary());
        reviewBody.put("event", reviewResult.toGitHubEvent());

        // Build inline comments
        List<Map<String, Object>> comments = new ArrayList<>();
        for (ReviewComment comment : reviewResult.getComments()) {
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("path", comment.getPath());
            commentMap.put("body", comment.getBody());
            commentMap.put("side", comment.getSide() != null ? comment.getSide() : "RIGHT");

            if (comment.getStartLine() != null && comment.getStartLine() != comment.getLine()) {
                commentMap.put("start_line", comment.getStartLine());
                commentMap.put("start_side", comment.getStartSide() != null ? comment.getStartSide() : "RIGHT");
                commentMap.put("line", comment.getLine());
            } else {
                commentMap.put("line", comment.getLine());
            }

            comments.add(commentMap);
        }
        reviewBody.put("comments", comments);

        try {
            webClient.post()
                    .uri("/repos/{repo}/pulls/{pr}/reviews", repoFullName, prNumber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(reviewBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Successfully submitted review for {}/pull/{}", repoFullName, prNumber);
        } catch (Exception e) {
            log.error("Failed to submit review for {}/pull/{}: {}", repoFullName, prNumber, e.getMessage());
            throw new RuntimeException("Failed to submit GitHub review", e);
        }
    }

    /**
     * Posts a simple comment on a pull request (non-review comment).
     */
    public void postComment(String repoFullName, int prNumber, String body) {
        log.info("Posting comment on {}/pull/{}", repoFullName, prNumber);

        Map<String, String> commentBody = Map.of("body", body);

        webClient.post()
                .uri("/repos/{repo}/issues/{pr}/comments", repoFullName, prNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
