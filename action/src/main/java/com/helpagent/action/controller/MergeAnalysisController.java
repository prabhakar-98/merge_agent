package com.helpagent.action.controller;

import com.helpagent.action.agent.MergeHelpAgentRunner;
import com.helpagent.action.service.GitHubOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for triggering merge conflict analysis with OAuth authentication.
 * This provides an API endpoint that can be called by authenticated users to analyze
 * pull requests using their OAuth tokens.
 */
@RestController
@RequestMapping("/api/merge-analysis")
public class MergeAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(MergeAnalysisController.class);

    private final MergeHelpAgentRunner agentRunner;
    private final GitHubOAuthService oauthService;

    public MergeAnalysisController(MergeHelpAgentRunner agentRunner, GitHubOAuthService oauthService) {
        this.agentRunner = agentRunner;
        this.oauthService = oauthService;
    }

    /**
     * Triggers merge conflict analysis for a pull request using OAuth authentication.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param oauthUserId OAuth user ID (from session or header)
     * @return Response indicating the analysis has been started
     */
    @PostMapping("/{owner}/{repo}/pull/{prNumber}")
    public ResponseEntity<Map<String, Object>> analyzePullRequest(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int prNumber,
            @RequestHeader(value = "X-OAuth-User-Id", required = false) String oauthUserId,
            @RequestBody(required = false) Map<String, String> requestBody) {

        log.info("Received merge analysis request for {}/{} PR#{} from OAuth user: {}",
                owner, repo, prNumber, oauthUserId);

        // Validate OAuth user has a valid token
        if (oauthUserId != null && !oauthService.hasValidToken(oauthUserId)) {
            log.warn("OAuth user {} does not have a valid token", oauthUserId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Invalid or expired OAuth token",
                            "message", "Please re-authenticate with GitHub OAuth"
                    ));
        }

        // Extract branch information from request body if provided
        String baseBranch = requestBody != null ? requestBody.get("baseBranch") : "main";
        String featureBranch = requestBody != null ? requestBody.get("featureBranch") : null;
        String prTitle = requestBody != null ? requestBody.get("prTitle") : "Pull Request #" + prNumber;

        // If feature branch not provided, we'll need to fetch it from GitHub
        // For now, we'll use a placeholder
        if (featureBranch == null) {
            featureBranch = "feature-branch"; // In production, fetch from GitHub API
        }

        // Trigger async analysis
        agentRunner.runMergeAnalysis(owner, repo, prNumber, baseBranch, featureBranch, prTitle, oauthUserId);

        return ResponseEntity.ok(Map.of(
                "status", "analysis_started",
                "repository", owner + "/" + repo,
                "pullRequest", prNumber,
                "baseBranch", baseBranch,
                "featureBranch", featureBranch,
                "authenticatedUser", oauthUserId != null ? oauthUserId : "anonymous",
                "message", "Merge conflict analysis has been started. Results will be posted as PR comments."
        ));
    }

    /**
     * Synchronous endpoint for testing - runs analysis and returns the result.
     */
    @PostMapping("/{owner}/{repo}/pull/{prNumber}/sync")
    public ResponseEntity<Map<String, Object>> analyzePullRequestSync(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int prNumber,
            @RequestHeader(value = "X-OAuth-User-Id", required = false) String oauthUserId,
            @RequestBody(required = false) Map<String, String> requestBody) {

        log.info("Received SYNC merge analysis request for {}/{} PR#{} from OAuth user: {}",
                owner, repo, prNumber, oauthUserId);

        // Validate OAuth user has a valid token
        if (oauthUserId != null && !oauthService.hasValidToken(oauthUserId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Invalid or expired OAuth token",
                            "message", "Please re-authenticate with GitHub OAuth"
                    ));
        }

        String baseBranch = requestBody != null ? requestBody.get("baseBranch") : "main";
        String featureBranch = requestBody != null ? requestBody.get("featureBranch") : "feature-branch";
        String prTitle = requestBody != null ? requestBody.get("prTitle") : "Pull Request #" + prNumber;

        try {
            // Run synchronous analysis
            String result = agentRunner.runMergeAnalysisSync(
                    owner, repo, prNumber, baseBranch, featureBranch, prTitle, oauthUserId);

            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "repository", owner + "/" + repo,
                    "pullRequest", prNumber,
                    "authenticatedUser", oauthUserId != null ? oauthUserId : "anonymous",
                    "result", result
            ));
        } catch (Exception e) {
            log.error("Sync analysis failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Analysis failed",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Health check endpoint to verify the service is running.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "merge-analysis-api"
        ));
    }
}
