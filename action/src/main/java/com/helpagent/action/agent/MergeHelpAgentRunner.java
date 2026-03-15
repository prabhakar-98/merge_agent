package com.helpagent.action.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.helpagent.action.tools.OAuthContext;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner service for the Merge Help Agent.
 * Executes the agent asynchronously when a PR webhook is received.
 * Supports OAuth authentication when a user ID is provided.
 */
@Service
public class MergeHelpAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(MergeHelpAgentRunner.class);

    private final LlmAgent mergeHelpAgent;

    public MergeHelpAgentRunner(@Qualifier("mergeHelpAgent") LlmAgent mergeHelpAgent) {
        this.mergeHelpAgent = mergeHelpAgent;
    }

    /**
     * Runs the merge help analysis asynchronously.
     * This is triggered by the webhook controller and runs in a background thread.
     *
     * @param owner         Repository owner
     * @param repo          Repository name
     * @param prNumber      Pull request number
     * @param baseBranch    Base branch (e.g., "main")
     * @param featureBranch Feature branch (e.g., "feature/new-thing")
     * @param prTitle       PR title for context
     */
    @Async
    public void runMergeAnalysis(String owner, String repo, int prNumber,
                                  String baseBranch, String featureBranch, String prTitle) {
        runMergeAnalysis(owner, repo, prNumber, baseBranch, featureBranch, prTitle, null);
    }

    /**
     * Runs the merge help analysis asynchronously with OAuth authentication.
     * This is triggered by the webhook controller and runs in a background thread.
     *
     * @param owner         Repository owner
     * @param repo          Repository name
     * @param prNumber      Pull request number
     * @param baseBranch    Base branch (e.g., "main")
     * @param featureBranch Feature branch (e.g., "feature/new-thing")
     * @param prTitle       PR title for context
     * @param oauthUserId   OAuth user ID for authentication (optional)
     */
    @Async
    public void runMergeAnalysis(String owner, String repo, int prNumber,
                                  String baseBranch, String featureBranch, String prTitle,
                                  String oauthUserId) {

        log.info("Starting merge conflict analysis for {}/{} PR#{}: {} <- {}{}",
                owner, repo, prNumber, baseBranch, featureBranch,
                oauthUserId != null ? " (with OAuth user: " + oauthUserId + ")" : "");

        // Set OAuth context for this thread
        if (oauthUserId != null && !oauthUserId.isBlank()) {
            OAuthContext.setOAuthUserId(oauthUserId);
            log.info("Set OAuth context for user: {}", oauthUserId);
        }

        try {
            InMemoryRunner runner = new InMemoryRunner(mergeHelpAgent);
            RunConfig runConfig = RunConfig.builder().build();

            String userId = "merge-bot-" + UUID.randomUUID().toString().substring(0, 8);
            String sessionId = "merge-" + owner + "-" + repo + "-pr" + prNumber;

            // Create context variables for the session
            ConcurrentHashMap<String, Object> contextVariables = new ConcurrentHashMap<>();
            contextVariables.put("number", prNumber);  // Changed from "prNumber" to "number"
            contextVariables.put("owner", owner);
            contextVariables.put("repo", repo);
            contextVariables.put("baseBranch", baseBranch);
            contextVariables.put("featureBranch", featureBranch);
            contextVariables.put("prTitle", prTitle);

            // Add OAuth user ID if provided
            if (oauthUserId != null && !oauthUserId.isBlank()) {
                contextVariables.put("oauthUserId", oauthUserId);
                log.info("Added OAuth user ID to context: {}", oauthUserId);
            }

            Session session = runner.sessionService()
                    .createSession(runner.appName(), userId, contextVariables, sessionId)
                    .blockingGet();

            log.info("Created merge session: {} for user: {}", session.id(), userId);

            // Build the analysis prompt with full context
            String prompt = buildPrompt(owner, repo, prNumber, baseBranch, featureBranch, prTitle);
            Content userMessage = Content.fromParts(Part.fromText(prompt));

            // Run the agent
            StringBuilder agentResponse = new StringBuilder();
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage, runConfig);

            events.blockingForEach(event -> {
                if (event.finalResponse()) {
                    String content = event.stringifyContent();
                    if (!content.isBlank()) {
                        agentResponse.append(content);
                    }
                    log.debug("Final merge agent response event received");
                } else {
                    log.debug("Merge agent event: author={}", event.author());
                }
            });

            String response = agentResponse.toString();
            log.info("Merge analysis completed for {}/{}/pull/{}. Response length: {} chars",
                    owner, repo, prNumber, response.length());

        } catch (Exception e) {
            log.error("Merge analysis failed for {}/{}/pull/{}: {}",
                    owner, repo, prNumber, e.getMessage(), e);

            // Attempt to comment the failure on the PR
            try {
                commentFailure(owner, repo, prNumber, e.getMessage());
            } catch (Exception commentEx) {
                log.error("Failed to post error comment on PR: {}", commentEx.getMessage());
            }
        } finally {
            // Clear OAuth context
            OAuthContext.clear();
        }
    }

    /**
     * Synchronous version for testing or direct invocation.
     */
    public String runMergeAnalysisSync(String owner, String repo, int prNumber,
                                        String baseBranch, String featureBranch, String prTitle) {
        return runMergeAnalysisSync(owner, repo, prNumber, baseBranch, featureBranch, prTitle, null);
    }

    /**
     * Synchronous version for testing or direct invocation with OAuth authentication.
     */
    public String runMergeAnalysisSync(String owner, String repo, int prNumber,
                                        String baseBranch, String featureBranch, String prTitle,
                                        String oauthUserId) {
        log.info("Starting SYNC merge conflict analysis for {}/{} PR#{}{}",
                owner, repo, prNumber,
                oauthUserId != null ? " (with OAuth user: " + oauthUserId + ")" : "");

        // Set OAuth context for this thread
        if (oauthUserId != null && !oauthUserId.isBlank()) {
            OAuthContext.setOAuthUserId(oauthUserId);
            log.info("Set OAuth context for user: {}", oauthUserId);
        }

        try {
            InMemoryRunner runner = new InMemoryRunner(mergeHelpAgent);
            RunConfig runConfig = RunConfig.builder().build();

            String userId = "merge-bot-" + UUID.randomUUID().toString().substring(0, 8);
            String sessionId = "merge-" + owner + "-" + repo + "-pr" + prNumber;

            // Create context variables for the session
            ConcurrentHashMap<String, Object> contextVariables = new ConcurrentHashMap<>();
            contextVariables.put("number", prNumber);  // Changed from "prNumber" to "number"
            contextVariables.put("owner", owner);
            contextVariables.put("repo", repo);
            contextVariables.put("baseBranch", baseBranch);
            contextVariables.put("featureBranch", featureBranch);
            contextVariables.put("prTitle", prTitle);

            // Add OAuth user ID if provided
            if (oauthUserId != null && !oauthUserId.isBlank()) {
                contextVariables.put("oauthUserId", oauthUserId);
                log.info("Added OAuth user ID to context: {}", oauthUserId);
            }

            Session session = runner.sessionService()
                    .createSession(runner.appName(), userId, contextVariables, sessionId)
                    .blockingGet();

            String prompt = buildPrompt(owner, repo, prNumber, baseBranch, featureBranch, prTitle);
            Content userMessage = Content.fromParts(Part.fromText(prompt));

            StringBuilder agentResponse = new StringBuilder();
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage, runConfig);

            events.blockingForEach(event -> {
                if (event.finalResponse()) {
                    String content = event.stringifyContent();
                    if (!content.isBlank()) {
                        agentResponse.append(content);
                    }
                }
            });

            return agentResponse.toString();
        } finally {
            // Clear OAuth context
            OAuthContext.clear();
        }
    }

    private String buildPrompt(String owner, String repo, int prNumber,
                                String baseBranch, String featureBranch, String prTitle) {
        return String.format("""
                Analyze the following GitHub Pull Request for merge conflicts and resolve them if found.
                
                ## Pull Request Details
                - **Repository**: %s/%s
                - **PR Number**: #%d
                - **PR Title**: %s
                - **Base Branch**: %s
                - **Feature Branch**: %s
                
                ## Instructions
                1. First, fetch the branch information for both "%s" and "%s"
                2. Then detect merge conflicts for PR #%d
                3. If conflicts exist, analyze them, generate resolutions, create a resolution branch, and comment on the PR
                4. If no conflicts, post a clean status comment on the PR
                
                Begin the analysis now.
                """,
                owner, repo, prNumber, prTitle, baseBranch, featureBranch,
                baseBranch, featureBranch, prNumber);
    }

    private void commentFailure(String owner, String repo, int prNumber, String errorMessage) {
        // Use the GitHubApiService directly for error reporting
        String comment = String.format("""
                ## ⚠️ Merge Help Agent — Error
                
                The automated merge conflict analysis encountered an error:
                
                ```
                %s
                ```
                
                Please review the conflicts manually or retry by pushing a new commit.
                """, errorMessage);

        // We can't inject GitHubApiService here without a circular dependency,
        // so we log the failure. The agent's tools already handle commenting.
        log.error("Would post failure comment to PR #{} in {}/{}: {}", prNumber, owner, repo, comment);
    }
}
