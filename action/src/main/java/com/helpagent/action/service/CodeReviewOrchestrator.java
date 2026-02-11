package com.helpagent.action.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.agent.CodeReviewAgentRunner;
import com.helpagent.action.model.PullRequestEvent;
import com.helpagent.action.model.ReviewComment;
import com.helpagent.action.model.ReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orchestrates the full code review lifecycle:
 * 1. Receives PR events from the webhook controller
 * 2. Triggers the AI agent to perform the review
 * 3. Parses the agent's response
 * 4. Publishes the review back to GitHub
 */
@Service
public class CodeReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewOrchestrator.class);

    private final CodeReviewAgentRunner agentRunner;
    private final GitHubApiService gitHubApiService;
    private final ObjectMapper objectMapper;

    @Value("${agent.max-files-per-review:50}")
    private int maxFilesPerReview;

    @Value("${agent.max-diff-size-bytes:500000}")
    private int maxDiffSizeBytes;

    public CodeReviewOrchestrator(
            CodeReviewAgentRunner agentRunner,
            GitHubApiService gitHubApiService,
            ObjectMapper objectMapper) {
        this.agentRunner = agentRunner;
        this.gitHubApiService = gitHubApiService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a pull request event asynchronously.
     * This is the main entry point called by the webhook controller.
     */
    @Async
    public void handlePullRequest(PullRequestEvent event) {
        String repoFullName = event.getRepository().getFullName();
        int prNumber = event.getPullRequest().getNumber();
        String headSha = event.getPullRequest().getHead().getSha();
        String action = event.getAction();

        log.info("Processing PR event: action={}, repo={}, pr=#{}, sha={}",
                action, repoFullName, prNumber, headSha);

        // Only review on open and synchronize (new push) events
        if (!isReviewableAction(action)) {
            log.info("Skipping PR action '{}' — not reviewable", action);
            return;
        }

        // Guard: skip if too many changed files
        if (event.getPullRequest().getChangedFiles() > maxFilesPerReview) {
            log.warn("PR #{} has {} changed files (max: {}). Posting size warning.",
                    prNumber, event.getPullRequest().getChangedFiles(), maxFilesPerReview);
            gitHubApiService.postComment(repoFullName, prNumber,
                    "⚠️ **AI Review Skipped**: This PR has %d changed files, exceeding the review limit of %d. Please break it into smaller PRs for automated review."
                            .formatted(event.getPullRequest().getChangedFiles(), maxFilesPerReview));
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Run the AI agent
            String agentResponse = agentRunner.runReview(repoFullName, prNumber, headSha);

            // Parse the agent's JSON response into a ReviewResult
            ReviewResult reviewResult = parseAgentResponse(agentResponse, repoFullName, prNumber, headSha);
            reviewResult.setDurationMs(System.currentTimeMillis() - startTime);

            log.info("Review completed in {}ms: {}", reviewResult.getDurationMs(), reviewResult);

            // Submit the review to GitHub
            gitHubApiService.submitReview(repoFullName, prNumber, reviewResult);

            log.info("Successfully submitted AI review for {}/pull/{}", repoFullName, prNumber);

        } catch (Exception e) {
            log.error("Failed to process PR review for {}/pull/{}: {}", repoFullName, prNumber, e.getMessage(), e);

            // Post an error comment so the PR author knows something went wrong
            try {
                gitHubApiService.postComment(repoFullName, prNumber,
                        "⚠️ **AI Review Error**: The automated code review encountered an error. A maintainer has been notified. Error: `%s`"
                                .formatted(e.getMessage()));
            } catch (Exception commentError) {
                log.error("Failed to post error comment", commentError);
            }
        }
    }

    /**
     * Parses the agent's JSON response into a structured ReviewResult.
     */
    private ReviewResult parseAgentResponse(String agentResponse, String repoFullName, int prNumber, String headSha) {
        ReviewResult result = new ReviewResult();
        result.setRepoFullName(repoFullName);
        result.setPullRequestNumber(prNumber);
        result.setCommitSha(headSha);
        result.setReviewedAt(Instant.now());

        try {
            // Strip potential markdown code fences
            String jsonStr = agentResponse.strip();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.strip();

            JsonNode root = objectMapper.readTree(jsonStr);

            // Parse verdict
            String verdict = root.path("verdict").asText("COMMENT");
            result.setVerdict(switch (verdict.toUpperCase()) {
                case "APPROVE" -> ReviewResult.ReviewVerdict.APPROVE;
                case "REQUEST_CHANGES" -> ReviewResult.ReviewVerdict.REQUEST_CHANGES;
                default -> ReviewResult.ReviewVerdict.COMMENT;
            });

            // Parse summary
            result.setSummary(root.path("summary").asText("AI code review completed."));

            // Parse inline comments
            JsonNode comments = root.path("comments");
            if (comments.isArray()) {
                for (JsonNode commentNode : comments) {
                    String path = commentNode.path("path").asText();
                    int line = commentNode.path("line").asInt(1);
                    String body = commentNode.path("body").asText();
                    String severity = commentNode.path("severity").asText("suggestion");

                    if (path.isBlank() || body.isBlank()) continue;

                    // Prepend severity emoji
                    String enrichedBody = enrichCommentBody(body, severity);

                    ReviewComment comment = new ReviewComment(path, line, enrichedBody);
                    result.addComment(comment);
                }
            }

            log.info("Parsed review: verdict={}, summary length={}, comments={}",
                    result.getVerdict(), result.getSummary().length(), result.getComments().size());

        } catch (JsonProcessingException e) {
            log.error("Failed to parse agent JSON response: {}", e.getMessage());
            log.debug("Raw agent response: {}", agentResponse);

            // Fallback: post the raw response as a general comment
            result.setVerdict(ReviewResult.ReviewVerdict.COMMENT);
            result.setSummary("AI Review (raw output — JSON parsing failed):\n\n" + agentResponse);
        }

        return result;
    }

    /**
     * Enriches comment body with severity indicators.
     */
    private String enrichCommentBody(String body, String severity) {
        String emoji = switch (severity.toLowerCase()) {
            case "critical" -> "🔴";
            case "warning" -> "🟡";
            case "suggestion" -> "💡";
            case "nitpick" -> "🔵";
            default -> "💬";
        };
        return emoji + " " + body;
    }

    /**
     * Determines if a PR action should trigger a review.
     */
    private boolean isReviewableAction(String action) {
        return "opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action);
    }
}
