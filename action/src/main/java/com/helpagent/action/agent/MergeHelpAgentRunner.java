package com.helpagent.action.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import com.helpagent.action.model.entity.AgentEventEntity;
import com.helpagent.action.model.entity.AgentWorkflowEntity;
import com.helpagent.action.model.entity.WorkflowStatus;
import com.helpagent.action.repository.AgentEventRepository;
import com.helpagent.action.repository.AgentWorkflowRepository;
import com.helpagent.action.service.GitHubApiService;
import com.helpagent.action.service.ReviewReportService;
import com.helpagent.action.tools.OAuthContext;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runner service for the Merge Help Agent.
 * Executes the agent asynchronously when a PR webhook is received.
 * Captures every ADK Event and persists it to PostgreSQL for dashboard tracking.
 * Supports OAuth authentication when a user ID is provided.
 */
@Service
public class MergeHelpAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(MergeHelpAgentRunner.class);

    /**
     * Maps tool function names to human-readable step labels for the dashboard UI.
     */
    private static final Map<String, String> TOOL_STEP_LABELS = Map.ofEntries(
            Map.entry("fetchBranchInfo", "Fetching branch info"),
            Map.entry("detectMergeConflicts", "Detecting conflicts"),
            Map.entry("fetchFileContent", "Reading file content"),
            Map.entry("searchConflictPatterns", "Searching RAG patterns"),
            Map.entry("validateCodeInSandbox", "Validating in sandbox"),
            Map.entry("createResolutionBranch", "Creating resolution branch"),
            Map.entry("commitResolvedFile", "Committing resolved file"),
            Map.entry("commentOnPullRequest", "Commenting on PR"),
            Map.entry("storeConflictPattern", "Storing pattern"),
            Map.entry("generateHumanReviewReport", "Generating review report"),
            Map.entry("exitLoop", "Finishing")
    );

    private final BaseAgent mergeHelpAgent;
    private final ReviewReportService reviewReportService;
    private final GitHubApiService gitHubApiService;
    private final AgentWorkflowRepository workflowRepository;
    private final AgentEventRepository eventRepository;

    public MergeHelpAgentRunner(@Qualifier("mergeHelpAgent") BaseAgent mergeHelpAgent,
                                 ReviewReportService reviewReportService,
                                 GitHubApiService gitHubApiService,
                                 AgentWorkflowRepository workflowRepository,
                                 AgentEventRepository eventRepository) {
        this.mergeHelpAgent = mergeHelpAgent;
        this.reviewReportService = reviewReportService;
        this.gitHubApiService = gitHubApiService;
        this.workflowRepository = workflowRepository;
        this.eventRepository = eventRepository;
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

        // Create workflow entity and persist to DB
        String workflowId = UUID.randomUUID().toString();
        AgentWorkflowEntity workflow = createWorkflowEntity(
                workflowId, owner, repo, prNumber, prTitle, baseBranch, featureBranch, oauthUserId);
        workflowRepository.save(workflow);

        // Set OAuth context for this thread
        if (oauthUserId != null && !oauthUserId.isBlank()) {
            OAuthContext.setOAuthUserId(oauthUserId);
            log.info("Set OAuth context for user: {}", oauthUserId);
        }

        try {
            // Update status to RUNNING
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflow.setCurrentStep("Initializing agent");
            workflowRepository.save(workflow);

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
            contextVariables.put("maxIterations", 2);  // Track max loop iterations
            contextVariables.put("currentIteration", 1);  // Track current iteration

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

            // Run the agent and capture every event
            StringBuilder agentResponse = new StringBuilder();
            AtomicInteger sequenceCounter = new AtomicInteger(0);
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage, runConfig);

            events.blockingForEach(event -> {
                // 1. Persist every ADK event to PostgreSQL
                persistEvent(workflowId, event, sequenceCounter.getAndIncrement());

                // 2. Update workflow status based on event type
                updateWorkflowFromEvent(workflow, event);

                // 3. Existing logic: capture final response
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

            // If the LLM failed silently (ADK swallows exceptions), the response will be empty
            if (response.isBlank() && workflow.getStatus() == WorkflowStatus.RUNNING) {
                throw new RuntimeException("Agent produced empty response — LLM call likely failed (check logs for upstream errors)");
            }

            // Mark workflow as completed (unless already set to ESCALATED or HUMAN_IN_LOOP)
            if (workflow.getStatus() == WorkflowStatus.RUNNING) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
            }
            workflow.setAgentResponse(truncate(response, 10000));
            workflow.setCompletedAt(Instant.now());
            workflow.setCurrentStep("Done");
            workflowRepository.save(workflow);

        } catch (Exception e) {
            String errorCategory = classifyError(e);
            log.error("Merge analysis failed for {}/{}/pull/{} [{}]: {}",
                    owner, repo, prNumber, errorCategory, e.getMessage(), e);

            // Update workflow as failed
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(truncate(e.getMessage(), 2000));
            workflow.setErrorCategory(errorCategory);
            workflow.setCompletedAt(Instant.now());
            workflow.setCurrentStep("Failed");
            workflowRepository.save(workflow);

            notifyPrOfFailure(owner, repo, prNumber, baseBranch, featureBranch, prTitle, errorCategory, e);
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

        // Create workflow entity
        String workflowId = UUID.randomUUID().toString();
        AgentWorkflowEntity workflow = createWorkflowEntity(
                workflowId, owner, repo, prNumber, prTitle, baseBranch, featureBranch, oauthUserId);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(workflow);

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
            contextVariables.put("maxIterations", 2);
            contextVariables.put("currentIteration", 1);

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
            AtomicInteger sequenceCounter = new AtomicInteger(0);
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage, runConfig);

            events.blockingForEach(event -> {
                persistEvent(workflowId, event, sequenceCounter.getAndIncrement());
                updateWorkflowFromEvent(workflow, event);

                if (event.finalResponse()) {
                    String content = event.stringifyContent();
                    if (!content.isBlank()) {
                        agentResponse.append(content);
                    }
                }
            });

            String response = agentResponse.toString();

            // If the LLM failed silently (ADK swallows exceptions), the response will be empty
            if (response.isBlank() && workflow.getStatus() == WorkflowStatus.RUNNING) {
                throw new RuntimeException("Agent produced empty response — LLM call likely failed (check logs for upstream errors)");
            }

            // Mark workflow as completed
            if (workflow.getStatus() == WorkflowStatus.RUNNING) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
            }
            workflow.setAgentResponse(truncate(response, 10000));
            workflow.setCompletedAt(Instant.now());
            workflow.setCurrentStep("Done");
            workflowRepository.save(workflow);

            return response;
        } catch (Exception e) {
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(truncate(e.getMessage(), 2000));
            workflow.setErrorCategory(classifyError(e));
            workflow.setCompletedAt(Instant.now());
            workflowRepository.save(workflow);
            throw e;
        } finally {
            // Clear OAuth context
            OAuthContext.clear();
        }
    }

    // ─── Private Helpers ───

    private AgentWorkflowEntity createWorkflowEntity(String id, String owner, String repo,
                                                       int prNumber, String prTitle,
                                                       String baseBranch, String featureBranch,
                                                       String oauthUserId) {
        AgentWorkflowEntity wf = new AgentWorkflowEntity();
        wf.setId(id);
        wf.setOwner(owner);
        wf.setRepo(repo);
        wf.setPrNumber(prNumber);
        wf.setPrTitle(prTitle);
        wf.setBaseBranch(baseBranch);
        wf.setFeatureBranch(featureBranch);
        wf.setOauthUserId(oauthUserId);
        wf.setStatus(WorkflowStatus.QUEUED);
        wf.setCurrentStep("Queued");
        wf.setStartedAt(Instant.now());
        wf.setUpdatedAt(Instant.now());
        return wf;
    }

    /**
     * Persist an ADK Event to the agent_events table.
     */
    private void persistEvent(String workflowId, Event event, int sequenceNumber) {
        try {
            AgentEventEntity entity = new AgentEventEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setWorkflowId(workflowId);
            entity.setAuthor(event.author());
            entity.setSequenceNumber(sequenceNumber);
            entity.setFinalResponse(event.finalResponse());
            entity.setCreatedAt(Instant.now());

            // Determine event type and content summary
            String eventType = "TEXT";
            String contentSummary = "";

            List<FunctionCall> functionCalls = event.functionCalls();
            if (functionCalls != null && !functionCalls.isEmpty()) {
                eventType = "FUNCTION_CALL";
                String toolName = functionCalls.get(0).name().orElse("unknown");
                contentSummary = TOOL_STEP_LABELS.getOrDefault(toolName, toolName);
            } else if (!event.functionResponses().isEmpty()) {
                eventType = "FUNCTION_RESPONSE";
                contentSummary = "Tool result received";
            } else if (event.finalResponse()) {
                contentSummary = truncate(event.stringifyContent(), 300);
            } else {
                contentSummary = truncate(event.stringifyContent(), 300);
            }

            // Check for escalation
            boolean escalated = false;
            if (event.actions() != null && event.actions().escalate().orElse(false)) {
                eventType = "ESCALATE";
                contentSummary = "Escalating to human review";
                escalated = true;
            }

            entity.setEventType(eventType);
            entity.setContentSummary(contentSummary);
            entity.setContentFull(truncate(event.stringifyContent(), 5000));
            entity.setEscalated(escalated);

            eventRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist event for workflow {}: {}", workflowId, e.getMessage());
        }
    }

    /**
     * Update workflow status and currentStep based on the ADK event.
     */
    private void updateWorkflowFromEvent(AgentWorkflowEntity workflow, Event event) {
        try {
            // Check for escalation
            if (event.actions() != null && event.actions().escalate().orElse(false)) {
                workflow.setStatus(WorkflowStatus.ESCALATED);
                workflow.setCurrentStep("Escalated to human review");
                workflowRepository.save(workflow);
                return;
            }

            // Check for function calls to derive current step
            List<FunctionCall> functionCalls = event.functionCalls();
            if (functionCalls != null && !functionCalls.isEmpty()) {
                String toolName = functionCalls.get(0).name().orElse("unknown");
                String stepLabel = TOOL_STEP_LABELS.getOrDefault(toolName, "Processing: " + toolName);
                workflow.setCurrentStep(stepLabel);

                // Detect HUMAN_IN_LOOP status
                if ("generateHumanReviewReport".equals(toolName)) {
                    workflow.setStatus(WorkflowStatus.HUMAN_IN_LOOP);
                }

                workflow.setUpdatedAt(Instant.now());
                workflowRepository.save(workflow);
            } else if (event.finalResponse()) {
                workflow.setCurrentStep("Generating response");
                workflow.setUpdatedAt(Instant.now());
                workflowRepository.save(workflow);
            }
        } catch (Exception e) {
            log.warn("Failed to update workflow {} from event: {}", workflow.getId(), e.getMessage());
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

    /**
     * Two-tier notification: try rich report first, fall back to a simple comment.
     */
    private void notifyPrOfFailure(String owner, String repo, int prNumber,
                                    String baseBranch, String featureBranch,
                                    String prTitle, String errorCategory, Exception error) {
        // Tier 1: Rich failure report via ReviewReportService
        try {
            String report = reviewReportService.generateReviewReport(
                    owner, repo, prNumber, prTitle,
                    baseBranch, featureBranch,
                    "Agent encountered an internal error: **" + errorCategory + "**",
                    List.of("Error: " + error.getMessage()),
                    List.of(),
                    List.of(),
                    List.of(),
                    getSuggestionForError(errorCategory)
            );
            reviewReportService.postReviewAsComment(owner, repo, prNumber, report, null);
            log.info("Posted rich failure report on PR #{} [{}]", prNumber, errorCategory);
            return;
        } catch (Exception reportEx) {
            log.warn("Rich failure report failed for PR #{}, trying simple comment: {}",
                    prNumber, reportEx.getMessage());
        }

        // Tier 2: Simple direct comment as last resort
        try {
            String simpleComment = String.format(
                    "**Merge Help Agent — Error**\n\n" +
                    "The agent failed to analyze this PR.\n\n" +
                    "- **Error type**: %s\n" +
                    "- **Details**: %s\n\n" +
                    "_Please retry by pushing a new commit or resolve conflicts manually._",
                    errorCategory, truncate(error.getMessage(), 500));
            gitHubApiService.commentOnPR(owner, repo, prNumber, simpleComment, null);
            log.info("Posted simple failure comment on PR #{} [{}]", prNumber, errorCategory);
        } catch (Exception commentEx) {
            log.error("All notification attempts failed for PR #{} in {}/{} [{}]: {}",
                    prNumber, owner, repo, errorCategory, commentEx.getMessage());
        }
    }

    private String classifyError(Exception e) {
        if (e instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            if (status == 401 || status == 403) return "AUTH_EXPIRED";
            if (status == 429) return "RATE_LIMITED";
            if (status >= 500) return "GITHUB_UNAVAILABLE";
            return "GITHUB_ERROR_" + status;
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("timeout") || msg.contains("timed out")) return "TIMEOUT";
        if (msg.contains("docker") || msg.contains("container")) return "SANDBOX_UNAVAILABLE";
        if (msg.contains("oauth") || msg.contains("token")) return "AUTH_EXPIRED";
        if (msg.contains("model") || msg.contains("llm") || msg.contains("api key")) return "LLM_ERROR";
        if (msg.contains("no endpoints found") || msg.contains("empty response")) return "LLM_ERROR";
        return "INTERNAL_ERROR";
    }

    private String getSuggestionForError(String errorCategory) {
        return switch (errorCategory) {
            case "AUTH_EXPIRED" -> "OAuth token may have expired. Please re-authorize at the OAuth endpoint and retry.";
            case "RATE_LIMITED" -> "GitHub API rate limit hit. The agent will automatically retry on the next push. No action needed.";
            case "GITHUB_UNAVAILABLE" -> "GitHub API returned a server error. This is usually transient — retry by pushing a new commit.";
            case "TIMEOUT" -> "The analysis timed out. This may indicate a very large PR. Consider splitting into smaller PRs.";
            case "SANDBOX_UNAVAILABLE" -> "Docker sandbox is unavailable. The server administrator should check that Docker is running.";
            case "LLM_ERROR" -> "The LLM provider returned an error. Check API key configuration or try again later.";
            default -> "Please review the conflicts manually or retry by pushing a new commit.";
        };
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "unknown";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
