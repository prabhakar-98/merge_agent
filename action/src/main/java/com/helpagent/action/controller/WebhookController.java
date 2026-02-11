package com.helpagent.action.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.model.PullRequestEvent;
import com.helpagent.action.security.WebhookSignatureVerifier;
import com.helpagent.action.service.CodeReviewOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller that receives GitHub webhook events.
 * Endpoint: POST /api/webhook/github
 *
 * GitHub sends a POST request with:
 *   - X-GitHub-Event header: the event type (e.g. "pull_request")
 *   - X-Hub-Signature-256 header: HMAC-SHA256 signature of the payload
 *   - X-GitHub-Delivery header: unique delivery ID
 *   - JSON body: the event payload
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final CodeReviewOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public WebhookController(
            WebhookSignatureVerifier signatureVerifier,
            CodeReviewOrchestrator orchestrator,
            ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives GitHub webhook events.
     * We read the raw body to verify the signature, then deserialize.
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody byte[] rawBody) {

        log.info("Received GitHub webhook: event={}, delivery={}", eventType, deliveryId);

        // Step 1: Verify signature
        if (!signatureVerifier.isValid(signature, rawBody)) {
            log.warn("Rejecting webhook with invalid signature, delivery={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // Step 2: Only process pull_request events
        if (!"pull_request".equals(eventType)) {
            log.info("Ignoring non-pull_request event: {}", eventType);
            return ResponseEntity.ok(Map.of("message", "Event type '%s' ignored".formatted(eventType)));
        }

        // Step 3: Deserialize the payload
        PullRequestEvent event;
        try {
            event = objectMapper.readValue(rawBody, PullRequestEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize pull_request event: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payload: " + e.getMessage()));
        }

        // Step 4: Filter for reviewable actions
        String action = event.getAction();
        if (!isReviewTrigger(action)) {
            log.info("Ignoring pull_request action '{}' for PR #{}", action,
                    event.getPullRequest().getNumber());
            return ResponseEntity.ok(
                    Map.of("message", "Action '%s' does not trigger a review".formatted(action)));
        }

        // Step 5: Trigger the review asynchronously
        log.info("Triggering AI review for {}/pull/#{} (action={})",
                event.getRepository().getFullName(),
                event.getPullRequest().getNumber(),
                action);

        orchestrator.handlePullRequest(event);

        return ResponseEntity.accepted()
                .body(Map.of(
                        "message", "Review triggered",
                        "repo", event.getRepository().getFullName(),
                        "pr", String.valueOf(event.getPullRequest().getNumber()),
                        "delivery", deliveryId != null ? deliveryId : "unknown"
                ));
    }

    /**
     * Health-check endpoint for monitoring.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private boolean isReviewTrigger(String action) {
        return "opened".equals(action)
                || "synchronize".equals(action)
                || "reopened".equals(action);
    }
}
