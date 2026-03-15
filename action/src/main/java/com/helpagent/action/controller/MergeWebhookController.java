package com.helpagent.action.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.agent.MergeHelpAgentRunner;
import com.helpagent.action.model.dto.PullRequestEvent;
import com.helpagent.action.security.WebhookSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * REST controller that receives GitHub webhook events for pull requests.
 * 
 * <p>Configure your GitHub App webhook:
 * <ul>
 *   <li>Payload URL: https://your-domain.com/api/webhooks/github/merge</li>
 *   <li>Content type: application/json</li>
 *   <li>Events: Pull requests</li>
 *   <li>Webhook secret: Configure in application.yml (github.webhook-secret)</li>
 * </ul>
 * 
 * <p>Incoming webhooks are verified using HMAC-SHA256 signature validation
 * via {@link WebhookSignatureVerifier} to ensure they originate from GitHub.
 */
@RestController
@RequestMapping("/api/webhooks/github")
public class MergeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MergeWebhookController.class);

    /**
     * PR actions that should trigger merge conflict analysis.
     */
    private static final Set<String> TRIGGER_ACTIONS = Set.of(
            "opened",       // New PR created
            "synchronize",  // New commits pushed to PR
            "reopened",     // PR was closed and reopened
            "closed"        // PR was closed
    );

    private final MergeHelpAgentRunner mergeHelpAgentRunner;
    private final ObjectMapper objectMapper;
    private final WebhookSignatureVerifier signatureVerifier;

    public MergeWebhookController(MergeHelpAgentRunner mergeHelpAgentRunner,
                                   ObjectMapper objectMapper,
                                   WebhookSignatureVerifier signatureVerifier) {
        this.mergeHelpAgentRunner = mergeHelpAgentRunner;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * Receives GitHub pull_request webhook events and triggers merge analysis.
     * Returns 202 Accepted immediately; analysis runs asynchronously.
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> handlePullRequestWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Received GitHub webhook: event={}, delivery={}", eventType, deliveryId);

        // Log the raw webhook payload
        log.debug("Raw webhook payload: {}", rawPayload);

        // For debugging, also log first 500 chars at INFO level
        if (rawPayload != null && rawPayload.length() > 0) {
            String preview = rawPayload.length() > 500 ?
                rawPayload.substring(0, 500) + "..." : rawPayload;
            log.info("Webhook payload preview: {}", preview);
        }

        // Verify webhook signature (HMAC-SHA256) - always use the original raw payload
        if (!signatureVerifier.isValid(signature, rawPayload != null ? rawPayload.getBytes() : new byte[0])) {
            log.warn("Webhook signature verification failed for delivery={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "error",
                    "message", "Invalid webhook signature"
            ));
        }

        // Only handle pull_request events
        if (!"pull_request".equals(eventType)) {
            log.info("Ignoring non-pull_request event: {}", eventType);
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "Not a pull_request event",
                    "event", eventType != null ? eventType : "unknown"
            ));
        }

        try {
            // First, try to parse as a wrapped payload (e.g., from a proxy or custom webhook sender)
            PullRequestEvent event = null;
            String actualPayload = rawPayload;

            // Check if the payload is wrapped in a "payload" field
            try {
                Map<String, Object> wrappedPayload = objectMapper.readValue(rawPayload, Map.class);
                if (wrappedPayload.containsKey("payload") && wrappedPayload.get("payload") instanceof String) {
                    // Extract the actual payload from the wrapper
                    actualPayload = (String) wrappedPayload.get("payload");
                    log.info("Detected wrapped payload format, extracting inner payload");
                }
            } catch (Exception e) {
                // Not a wrapped format, continue with original payload
                log.debug("Payload is not in wrapped format, using as-is");
            }

            // Parse the actual payload
            event = objectMapper.readValue(actualPayload, PullRequestEvent.class);

            // Log parsed event details
            log.info("Parsed PR event - Action: {}, PR Number: {}",
                event.getAction(),
                event.getNumber());

            // Check if action is null
            if (event.getAction() == null) {
                log.warn("PR event has null action field");
                return ResponseEntity.ok(Map.of(
                        "status", "ignored",
                        "reason", "PR action is null",
                        "pr", event.getNumber()
                ));
            }

            // Only process relevant PR actions
            if (!TRIGGER_ACTIONS.contains(event.getAction())) {
                log.info("Ignoring PR action: {}", event.getAction());
                return ResponseEntity.ok(Map.of(
                        "status", "ignored",
                        "reason", "Action '" + event.getAction() + "' does not trigger analysis",
                        "pr", event.getNumber()
                ));
            }

            // Extract PR details
            String owner = event.getRepository().getOwner().getLogin();
            String repo = event.getRepository().getName();
            int prNumber = event.getNumber();
            String baseBranch = event.getPullRequest().getBase().getRef();
            String featureBranch = event.getPullRequest().getHead().getRef();
            String prTitle = event.getPullRequest().getTitle();
            
            // Extract PR author's user ID for OAuth authentication
            String oauthUserId = null;
            if (event.getPullRequest().getUser() != null && event.getPullRequest().getUser().getId() > 0) {
                oauthUserId = String.valueOf(event.getPullRequest().getUser().getId());
                log.info("Using OAuth authentication for user ID: {}", oauthUserId);
            }

            log.info("Processing PR #{} ({}) in {}/{}: {} <- {}",
                    prNumber, event.getAction(), owner, repo, baseBranch, featureBranch);

            // Trigger async merge analysis with OAuth user ID
            mergeHelpAgentRunner.runMergeAnalysis(
                    owner, repo, prNumber, baseBranch, featureBranch, prTitle, oauthUserId);

            return ResponseEntity.accepted().body(Map.of(
                    "status", "accepted",
                    "message", "Merge conflict analysis started",
                    "pr", prNumber,
                    "repository", owner + "/" + repo,
                    "baseBranch", baseBranch,
                    "featureBranch", featureBranch,
                    "timestamp", Instant.now().toString()
            ));

        } catch (Exception e) {
            log.error("Failed to process webhook payload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to process webhook: " + e.getMessage(),
                    "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Manual trigger endpoint for testing merge analysis without a webhook.
     * 
     * Example: POST /api/webhooks/github/merge/trigger
     * Body: { "owner": "octocat", "repo": "hello-world", "prNumber": 1 }
     */
    @PostMapping("/merge/trigger")
    public ResponseEntity<Map<String, Object>> triggerManualAnalysis(
            @RequestBody Map<String, Object> request) {

        String owner = (String) request.get("owner");
        String repo = (String) request.get("repo");
        int prNumber = (int) request.get("prNumber");
        String baseBranch = (String) request.getOrDefault("baseBranch", "main");
        String featureBranch = (String) request.getOrDefault("featureBranch", "");
        String prTitle = (String) request.getOrDefault("prTitle", "Manual merge analysis");

        if (owner == null || repo == null || prNumber <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Missing required fields: owner, repo, prNumber"
            ));
        }

        log.info("Manual merge analysis triggered for {}/{} PR#{}", owner, repo, prNumber);

        mergeHelpAgentRunner.runMergeAnalysis(
                owner, repo, prNumber, baseBranch, featureBranch, prTitle);

        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Manual merge conflict analysis started",
                "pr", prNumber,
                "repository", owner + "/" + repo,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Synchronous analysis endpoint for testing — waits for agent to finish and returns result.
     */
    @PostMapping("/merge/analyze")
    public ResponseEntity<Map<String, Object>> analyzeSync(
            @RequestBody Map<String, Object> request) {

        String owner = (String) request.get("owner");
        String repo = (String) request.get("repo");
        int prNumber = (int) request.get("prNumber");
        String baseBranch = (String) request.getOrDefault("baseBranch", "main");
        String featureBranch = (String) request.getOrDefault("featureBranch", "");
        String prTitle = (String) request.getOrDefault("prTitle", "Sync merge analysis");

        if (owner == null || repo == null || prNumber <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Missing required fields: owner, repo, prNumber"
            ));
        }

        log.info("Synchronous merge analysis for {}/{} PR#{}", owner, repo, prNumber);

        String result = mergeHelpAgentRunner.runMergeAnalysisSync(
                owner, repo, prNumber, baseBranch, featureBranch, prTitle);

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "pr", prNumber,
                "repository", owner + "/" + repo,
                "result", result,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Health check endpoint for the merge webhook.
     */
    @GetMapping("/merge/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "merge-help-agent",
                "timestamp", Instant.now().toString()
        ));
    }
}
