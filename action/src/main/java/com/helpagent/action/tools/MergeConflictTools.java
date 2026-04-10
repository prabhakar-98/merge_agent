package com.helpagent.action.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpagent.action.model.dto.MergeConflictInfo;
import com.helpagent.action.service.GitHubApiService;
import com.helpagent.action.service.GitHubOAuthService;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADK FunctionTools for merge conflict detection and resolution.
 * These tools are exposed to the LLM agent so it can orchestrate
 * the merge conflict workflow autonomously.
 *
 * Supports OAuth authentication when a user token is available in context.
 */
@Component
public class MergeConflictTools {

    private static final Logger log = LoggerFactory.getLogger(MergeConflictTools.class);

    private final GitHubApiService gitHubApiService;
    private final GitHubOAuthService oauthService;

    public MergeConflictTools(GitHubApiService gitHubApiService, GitHubOAuthService oauthService) {
        this.gitHubApiService = gitHubApiService;
        this.oauthService = oauthService;
    }

    /**
     * Fetches the latest SHA of a branch. Use this to get the current state of
     * the base branch or feature branch.
     */
    public Map<String, String> fetchBranchInfo(
            @Schema(description = "Repository owner (e.g., 'octocat')") String owner,
            @Schema(description = "Repository name (e.g., 'hello-world')") String repo,
            @Schema(description = "Branch name (e.g., 'main' or 'feature/my-feature')") String branch) {

        log.info("Tool: fetchBranchInfo({}/{}, {})", owner, repo, branch);

        String oauthToken = getOAuthToken();
        String sha = gitHubApiService.getBranchSha(owner, repo, branch, oauthToken);

        Map<String, String> result = new HashMap<>();
        result.put("branch", branch);
        result.put("sha", sha);
        result.put("status", "success");
        return result;
    }

    /**
     * Checks a pull request for merge conflicts. Returns conflict details including
     * the list of conflicting files and their diffs.
     */
    public Map<String, Object> detectMergeConflicts(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Pull request number") int prNumber,
            @Schema(description = "Base branch name (e.g., 'main')") String baseBranch,
            @Schema(description = "Feature branch name") String featureBranch) {

        log.info("Tool: detectMergeConflicts({}/{}, PR#{}, {} <- {})",
                owner, repo, prNumber, baseBranch, featureBranch);

        String oauthToken = getOAuthToken();
        MergeConflictInfo conflictInfo = gitHubApiService.checkForConflicts(
                owner, repo, prNumber, baseBranch, featureBranch, oauthToken);

        Map<String, Object> result = new HashMap<>();
        result.put("hasConflicts", conflictInfo.isHasConflicts());
        result.put("baseBranch", conflictInfo.getBaseBranch());
        result.put("featureBranch", conflictInfo.getFeatureBranch());
        result.put("totalChangedFiles", conflictInfo.getConflictFiles().size());

        // Provide file details for the agent
        List<Map<String, String>> fileDetails = conflictInfo.getConflictFiles().stream()
                .map(f -> {
                    Map<String, String> fm = new HashMap<>();
                    fm.put("filename", f.getFilename());
                    fm.put("status", f.getStatus());
                    fm.put("diff", f.getConflictDiff() != null
                            ? truncate(f.getConflictDiff(), 3000) : "no diff available");
                    return fm;
                })
                .collect(Collectors.toList());

        result.put("files", fileDetails);
        result.put("status", "success");
        return result;
    }

    /**
     * Fetches the content of a file from a specific branch. Use this to get the
     * full content of conflicting files from both branches.
     */
    public Map<String, String> fetchFileContent(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "File path in the repository") String filePath,
            @Schema(description = "Branch name or commit SHA to fetch from") String ref) {

        log.info("Tool: fetchFileContent({}/{}, {}, ref={})", owner, repo, filePath, ref);

        String oauthToken = getOAuthToken();
        String content = gitHubApiService.getFileContent(owner, repo, filePath, ref, oauthToken);

        Map<String, String> result = new HashMap<>();
        result.put("filePath", filePath);
        result.put("ref", ref);
        result.put("content", content != null ? content : "");
        result.put("status", content != null ? "success" : "not_found");
        return result;
    }

    /**
     * Gets the diff between two branches. Use this to see what changes
     * would be merged.
     */
    public Map<String, String> getBranchDiff(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Base branch (e.g., 'main')") String baseBranch,
            @Schema(description = "Head branch (e.g., 'feature/new-feature')") String headBranch) {

        log.info("Tool: getBranchDiff({}/{}, {} ... {})", owner, repo, baseBranch, headBranch);

        try {
            String oauthToken = getOAuthToken();
            String diff = gitHubApiService.compareBranches(owner, repo, baseBranch, headBranch, oauthToken);

            Map<String, String> result = new HashMap<>();
            result.put("baseBranch", baseBranch);
            result.put("headBranch", headBranch);
            result.put("diff", truncate(diff, 10000)); // Larger limit for full diffs
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("Failed to get branch diff: {}", e.getMessage());
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Creates a new branch for conflict resolution. Use this to create a branch
     * where resolved files will be committed.
     */
    public Map<String, String> createResolutionBranch(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "New branch name (e.g., 'merge-conflict-resolution-123')") String branchName,
            @Schema(description = "Base commit SHA to branch from") String baseSha) {

        log.info("Tool: createResolutionBranch({}/{}, {}, sha={})", owner, repo, branchName, baseSha);

        try {
            String oauthToken = getOAuthToken();
            gitHubApiService.createBranch(owner, repo, branchName, baseSha, oauthToken);

            Map<String, String> result = new HashMap<>();
            result.put("branch", branchName);
            result.put("baseSha", baseSha);
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("Failed to create branch: {}", e.getMessage());
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Commits a resolved file to the resolution branch. Use this after generating
     * the conflict resolution to save the merged content.
     */
    public Map<String, String> commitResolvedFile(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Branch to commit to") String branch,
            @Schema(description = "File path in the repository") String filePath,
            @Schema(description = "Resolved file content") String content,
            @Schema(description = "Commit message") String commitMessage) {

        log.info("Tool: commitResolvedFile({}/{}, {}, {})", owner, repo, branch, filePath);

        try {
            String oauthToken = getOAuthToken();
            gitHubApiService.createOrUpdateFile(owner, repo, branch, filePath, content, commitMessage, oauthToken);

            Map<String, String> result = new HashMap<>();
            result.put("branch", branch);
            result.put("filePath", filePath);
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("Failed to commit file: {}", e.getMessage());
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Posts a comment on the pull request. Use this to inform users about the
     * conflict resolution status or provide instructions.
     */
    public Map<String, String> commentOnPullRequest(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Pull request number") int prNumber,
            @Schema(description = "Comment body (supports GitHub Markdown)") String comment) {

        log.info("Tool: commentOnPullRequest({}/{}, PR#{})", owner, repo, prNumber);

        try {
            String oauthToken = getOAuthToken();
            gitHubApiService.commentOnPR(owner, repo, prNumber, comment, oauthToken);

            Map<String, String> result = new HashMap<>();
            result.put("prNumber", String.valueOf(prNumber));
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("Failed to post comment: {}", e.getMessage());
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Creates a pull request for the conflict resolution. Use this to propose
     * the resolved changes back to the original branch.
     */
    public Map<String, Object> createResolutionPullRequest(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "PR title") String title,
            @Schema(description = "PR description (supports GitHub Markdown)") String body,
            @Schema(description = "Head branch (source)") String head,
            @Schema(description = "Base branch (target)") String base) {

        log.info("Tool: createResolutionPullRequest({}/{}, {} -> {})", owner, repo, head, base);

        try {
            String oauthToken = getOAuthToken();
            JsonNode pr = gitHubApiService.createPullRequest(owner, repo, title, body, head, base, oauthToken);

            Map<String, Object> result = new HashMap<>();
            result.put("prNumber", pr.get("number").asInt());
            result.put("prUrl", pr.get("html_url").asText());
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            log.error("Failed to create PR: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Merges a pull request. Use this to merge a PR after resolving conflicts
     * or when the PR is ready to be merged.
     */
    public Map<String, Object> mergePullRequest(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Pull request number") int prNumber,
            @Schema(description = "Commit title (optional, null for default)") String commitTitle,
            @Schema(description = "Commit message (optional, null for default)") String commitMessage,
            @Schema(description = "Merge method: 'merge', 'squash', or 'rebase' (default: 'merge')") String mergeMethod) {

        log.info("Tool: mergePullRequest({}/{}, PR#{}, method={})", owner, repo, prNumber, mergeMethod);

        try {
            String oauthToken = getOAuthToken();
            JsonNode result = gitHubApiService.mergePullRequest(
                    owner, repo, prNumber, commitTitle, commitMessage, mergeMethod, oauthToken);

            Map<String, Object> response = new HashMap<>();
            response.put("prNumber", prNumber);
            response.put("merged", result.has("merged") && result.get("merged").asBoolean());
            response.put("sha", result.has("sha") ? result.get("sha").asText() : null);
            response.put("message", result.has("message") ? result.get("message").asText() : "PR merged successfully");
            response.put("status", "success");
            return response;
        } catch (Exception e) {
            log.error("Failed to merge PR: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Closes a pull request without merging. Use this to close a PR that
     * should not be merged (e.g., abandoned, duplicate, or invalid).
     */
    public Map<String, Object> closePullRequest(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Pull request number") int prNumber) {

        log.info("Tool: closePullRequest({}/{}, PR#{})", owner, repo, prNumber);

        try {
            String oauthToken = getOAuthToken();
            JsonNode result = gitHubApiService.closePullRequest(owner, repo, prNumber, oauthToken);

            Map<String, Object> response = new HashMap<>();
            response.put("prNumber", prNumber);
            response.put("state", result.has("state") ? result.get("state").asText() : "closed");
            response.put("htmlUrl", result.has("html_url") ? result.get("html_url").asText() : null);
            response.put("status", "success");
            return response;
        } catch (Exception e) {
            log.error("Failed to close PR: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * Call this function ONLY when the merge conflict has been successfully resolved
     * and all resolved files have been committed. This signals the loop to stop.
     */
    @Schema(description = "Call this function ONLY when the merge conflict has been successfully " +
            "resolved and all resolved files have been committed, signaling the iterative process should end.")
    public Map<String, String> exitLoop(@Schema(name = "toolContext") ToolContext toolContext) {
        log.info("[Tool Call] exitLoop triggered by {}", toolContext.agentName());
        toolContext.actions().setEscalate(true);
        Map<String, String> result = new HashMap<>();
        result.put("status", "loop_exited");
        result.put("message", "Merge resolution loop completed successfully.");
        return result;
    }

    /**
     * Helper method to get OAuth token from thread-local context.
     * Returns null if no OAuth user ID is provided or no token is found.
     */
    private String getOAuthToken() {
        String oauthUserId = OAuthContext.getOAuthUserId();
        if (oauthUserId == null || oauthUserId.isBlank()) {
            log.debug("No OAuth user ID in context, using default authentication");
            return null;
        }

        String token = oauthService.getAccessToken(oauthUserId);
        if (token != null) {
            log.debug("Using OAuth token for user: {}", oauthUserId);
        } else {
            log.debug("No OAuth token found for user: {}, using default authentication", oauthUserId);
        }
        return token;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
