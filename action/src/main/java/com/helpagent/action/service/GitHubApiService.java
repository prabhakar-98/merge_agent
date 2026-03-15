package com.helpagent.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.model.dto.MergeConflictInfo;
import com.helpagent.action.tools.OAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

/**
 * Service for interacting with the GitHub REST API.
 * Handles fetching branches, detecting conflicts, creating branches,
 * committing files, and commenting on PRs.
 *
 * <p>Authentication is handled via OAuth tokens. When no OAuth token is available,
 * operations will fail with an authentication error.
 */
@Service
public class GitHubApiService {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GitHubOAuthService oauthService;

    public GitHubApiService(ObjectMapper objectMapper, GitHubOAuthService oauthService) {
        this.objectMapper = objectMapper;
        this.oauthService = oauthService;
        this.webClient = WebClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "MergeHelpAgent/1.0")
                .build();
    }

    /**
     * Builds authorization headers for GitHub API calls.
     * Uses OAuth token from the current context or throws an error if not available.
     */
    private WebClient.RequestHeadersSpec<?> withAuth(WebClient.RequestHeadersSpec<?> spec) {
        String oauthToken = getOAuthTokenFromContext();
        if (oauthToken == null || oauthToken.isBlank()) {
            throw new IllegalStateException("No OAuth token available. Please authenticate first.");
        }
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthToken);
    }

    /**
     * Builds authorization headers using a specific OAuth token.
     * Use this for user-specific operations with OAuth authentication.
     */
    private WebClient.RequestHeadersSpec<?> withAuth(WebClient.RequestHeadersSpec<?> spec, String oauthToken) {
        if (oauthToken != null && !oauthToken.isBlank()) {
            log.debug("Using provided OAuth token for authentication");
            return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthToken);
        }
        return withAuth(spec);
    }

    /**
     * Gets OAuth token from the current context.
     */
    private String getOAuthTokenFromContext() {
        String oauthUserId = OAuthContext.getOAuthUserId();
        if (oauthUserId == null || oauthUserId.isBlank()) {
            log.debug("No OAuth user ID in context");
            return null;
        }

        String token = oauthService.getAccessToken(oauthUserId);
        if (token != null) {
            log.debug("Using OAuth token for user: {}", oauthUserId);
        } else {
            log.debug("No OAuth token found for user: {}", oauthUserId);
        }
        return token;
    }

    // ─── Branch Operations ───

    /**
     * Fetches the SHA of a branch's latest commit.
     */
    public String getBranchSha(String owner, String repo, String branch) {
        return getBranchSha(owner, repo, branch, null);
    }

    /**
     * Fetches the SHA of a branch's latest commit with optional OAuth token.
     */
    public String getBranchSha(String owner, String repo, String branch, String oauthToken) {
        log.info("Fetching SHA for branch: {}/{} -> {}", owner, repo, branch);
        String response = withAuth(
                webClient.get()
                        .uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", owner, repo, branch),
                oauthToken
        ).retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode node = objectMapper.readTree(response);
            String sha = node.at("/object/sha").asText();
            log.info("Branch {} SHA: {}", branch, sha);
            return sha;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse branch SHA for " + branch, e);
        }
    }

    /**
     * Fetches the content of a file at a specific ref (branch/SHA).
     */
    public String getFileContent(String owner, String repo, String path, String ref) {
        return getFileContent(owner, repo, path, ref, null);
    }

    /**
     * Fetches the content of a file at a specific ref with optional OAuth token.
     */
    public String getFileContent(String owner, String repo, String path, String ref, String oauthToken) {
        log.debug("Fetching file content: {}/{}/{} at ref {}", owner, repo, path, ref);
        try {
            String response = withAuth(
                    webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/repos/{owner}/{repo}/contents/{path}")
                                    .queryParam("ref", ref)
                                    .build(owner, repo, path)),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            String contentBase64 = node.get("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(contentBase64));
        } catch (Exception e) {
            log.warn("Could not fetch file {} at ref {}: {}", path, ref, e.getMessage());
            return null;
        }
    }

    // ─── PR & Merge Operations ───

    /**
     * Checks the mergeability of a PR and returns the PR JSON data.
     */
    public JsonNode getPullRequest(String owner, String repo, int prNumber) {
        return getPullRequest(owner, repo, prNumber, null);
    }

    /**
     * Checks the mergeability of a PR with optional OAuth token.
     */
    public JsonNode getPullRequest(String owner, String repo, int prNumber, String oauthToken) {
        log.info("Fetching PR #{} for {}/{}", prNumber, owner, repo);
        String response = withAuth(
                webClient.get()
                        .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber),
                oauthToken
        ).retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PR #" + prNumber, e);
        }
    }

    /**
     * Fetches the list of files changed in a pull request.
     */
    public List<JsonNode> getPullRequestFiles(String owner, String repo, int prNumber) {
        return getPullRequestFiles(owner, repo, prNumber, null);
    }

    /**
     * Fetches the list of files changed in a pull request with optional OAuth token.
     */
    public List<JsonNode> getPullRequestFiles(String owner, String repo, int prNumber, String oauthToken) {
        log.info("Fetching changed files for PR #{}", prNumber);
        String response = withAuth(
                webClient.get()
                        .uri("/repos/{owner}/{repo}/pulls/{prNumber}/files", owner, repo, prNumber),
                oauthToken
        ).retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode files = objectMapper.readTree(response);
            List<JsonNode> result = new ArrayList<>();
            files.forEach(result::add);
            log.info("PR #{} has {} changed files", prNumber, result.size());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PR files for #" + prNumber, e);
        }
    }

    /**
     * Compares two branches and returns the comparison data.
     * The diff reveals conflicting changes.
     */
    public String compareBranches(String owner, String repo, String base, String head) {
        return compareBranches(owner, repo, base, head, null);
    }

    /**
     * Compares two branches with optional OAuth token.
     */
    public String compareBranches(String owner, String repo, String base, String head, String oauthToken) {
        log.info("Comparing branches: {} ... {}", base, head);
        String response = withAuth(
                webClient.get()
                        .uri("/repos/{owner}/{repo}/compare/{base}...{head}", owner, repo, base, head)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff"),
                oauthToken
        ).retrieve()
                .bodyToMono(String.class)
                .block();

        return response;
    }

    /**
     * Attempts a test merge via the GitHub API to check for conflicts.
     * Returns the merge status info.
     */
    public MergeConflictInfo checkForConflicts(String owner, String repo, int prNumber,
                                                String baseBranch, String featureBranch) {
        return checkForConflicts(owner, repo, prNumber, baseBranch, featureBranch, null);
    }

    /**
     * Attempts a test merge via the GitHub API to check for conflicts with optional OAuth token.
     * Returns the merge status info.
     */
    public MergeConflictInfo checkForConflicts(String owner, String repo, int prNumber,
                                                String baseBranch, String featureBranch, String oauthToken) {
        log.info("Checking for merge conflicts: {} <- {}", baseBranch, featureBranch);

        // Get PR data to check mergeable status
        JsonNode prData = getPullRequest(owner, repo, prNumber, oauthToken);
        boolean mergeable = prData.has("mergeable") && !prData.get("mergeable").isNull()
                && prData.get("mergeable").asBoolean();
        String mergeableState = prData.has("mergeable_state")
                ? prData.get("mergeable_state").asText() : "unknown";

        log.info("PR #{} mergeable: {}, state: {}", prNumber, mergeable, mergeableState);

        // Fetch the diff between the two branches
        String diff = compareBranches(owner, repo, baseBranch, featureBranch, oauthToken);

        // Get changed files
        List<JsonNode> changedFiles = getPullRequestFiles(owner, repo, prNumber, oauthToken);

        List<MergeConflictInfo.ConflictFile> conflictFiles = new ArrayList<>();

        for (JsonNode file : changedFiles) {
            String filename = file.get("filename").asText();
            String status = file.get("status").asText();
            String patch = file.has("patch") ? file.get("patch").asText() : "";

            // Fetch file content from both branches for conflicted files
            String baseContent = getFileContent(owner, repo, filename, baseBranch, oauthToken);
            String featureContent = getFileContent(owner, repo, filename, featureBranch, oauthToken);

            MergeConflictInfo.ConflictFile conflictFile = new MergeConflictInfo.ConflictFile(
                    filename, baseContent, featureContent, patch, status
            );
            conflictFiles.add(conflictFile);
        }

        boolean hasConflicts = !mergeable || "dirty".equals(mergeableState);

        MergeConflictInfo info = new MergeConflictInfo(
                baseBranch, featureBranch, hasConflicts, conflictFiles, diff
        );

        log.info("Conflict check complete: {}", info);
        return info;
    }

    // ─── Branch Creation & File Commit ───

    /**
     * Creates a new branch from a given SHA.
     */
    public void createBranch(String owner, String repo, String branchName, String sha) {
        createBranch(owner, repo, branchName, sha, null);
    }

    /**
     * Creates a new branch from a given SHA with optional OAuth token.
     */
    public void createBranch(String owner, String repo, String branchName, String sha, String oauthToken) {
        log.info("Creating branch: {} from SHA: {}", branchName, sha);
        String ref = "refs/heads/" + branchName;

        Map<String, String> body = Map.of("ref", ref, "sha", sha);

        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            withAuth(
                    webClient.post()
                            .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Branch {} created successfully", branchName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create branch " + branchName, e);
        }
    }

    /**
     * Creates or updates a file on a specific branch via the GitHub Contents API.
     */
    public void createOrUpdateFile(String owner, String repo, String branch,
                                    String path, String content, String commitMessage) {
        createOrUpdateFile(owner, repo, branch, path, content, commitMessage, null);
    }

    /**
     * Creates or updates a file on a specific branch with optional OAuth token.
     */
    public void createOrUpdateFile(String owner, String repo, String branch,
                                    String path, String content, String commitMessage, String oauthToken) {
        log.info("Committing file {} to branch {}", path, branch);

        // Check if file exists to get SHA for update
        String existingSha = null;
        try {
            String response = withAuth(
                    webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/repos/{owner}/{repo}/contents/{path}")
                                    .queryParam("ref", branch)
                                    .build(owner, repo, path)),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            if (node.has("sha")) {
                existingSha = node.get("sha").asText();
            }
        } catch (Exception e) {
            // File doesn't exist yet, that's fine
            log.debug("File {} doesn't exist on branch {} — will create new", path, branch);
        }

        try {
            String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());
            Map<String, Object> body = new HashMap<>();
            body.put("message", commitMessage);
            body.put("content", encodedContent);
            body.put("branch", branch);
            if (existingSha != null) {
                body.put("sha", existingSha);
            }

            String bodyJson = objectMapper.writeValueAsString(body);
            withAuth(
                    webClient.put()
                            .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("File {} committed to branch {} successfully", path, branch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to commit file " + path + " to " + branch, e);
        }
    }

    // ─── PR Comments ───

    /**
     * Posts a comment on a pull request.
     */
    public void commentOnPR(String owner, String repo, int prNumber, String body) {
        commentOnPR(owner, repo, prNumber, body, null);
    }

    /**
     * Posts a comment on a pull request with optional OAuth token.
     */
    public void commentOnPR(String owner, String repo, int prNumber, String body, String oauthToken) {
        log.info("Posting comment on PR #{} (length: {} chars)", prNumber, body.length());

        try {
            Map<String, String> commentBody = Map.of("body", body);
            String bodyJson = objectMapper.writeValueAsString(commentBody);

            withAuth(
                    webClient.post()
                            .uri("/repos/{owner}/{repo}/issues/{prNumber}/comments", owner, repo, prNumber)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Comment posted on PR #{}", prNumber);
        } catch (Exception e) {
            throw new RuntimeException("Failed to post comment on PR #" + prNumber, e);
        }
    }

    /**
     * Creates a new pull request.
     */
    public JsonNode createPullRequest(String owner, String repo, String title,
                                       String body, String head, String base) {
        return createPullRequest(owner, repo, title, body, head, base, null);
    }

    /**
     * Creates a new pull request with optional OAuth token.
     */
    public JsonNode createPullRequest(String owner, String repo, String title,
                                       String body, String head, String base, String oauthToken) {
        log.info("Creating PR: {} -> {} in {}/{}", head, base, owner, repo);

        try {
            Map<String, String> prBody = Map.of(
                    "title", title,
                    "body", body,
                    "head", head,
                    "base", base
            );
            String bodyJson = objectMapper.writeValueAsString(prBody);

            String response = withAuth(
                    webClient.post()
                            .uri("/repos/{owner}/{repo}/pulls", owner, repo)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PR in " + owner + "/" + repo, e);
        }
    }

    // ─── Merge & Close PR ───

    /**
     * Merges a pull request.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param commitTitle Optional commit title (null uses default)
     * @param commitMessage Optional commit message (null uses default)
     * @param mergeMethod Merge method: "merge", "squash", or "rebase" (null defaults to "merge")
     * @return Merge result as JsonNode
     */
    public JsonNode mergePullRequest(String owner, String repo, int prNumber,
                                      String commitTitle, String commitMessage, String mergeMethod) {
        return mergePullRequest(owner, repo, prNumber, commitTitle, commitMessage, mergeMethod, null);
    }

    /**
     * Merges a pull request with optional OAuth token.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param commitTitle Optional commit title (null uses default)
     * @param commitMessage Optional commit message (null uses default)
     * @param mergeMethod Merge method: "merge", "squash", or "rebase" (null defaults to "merge")
     * @param oauthToken OAuth token for authentication
     * @return Merge result as JsonNode
     */
    public JsonNode mergePullRequest(String owner, String repo, int prNumber,
                                      String commitTitle, String commitMessage, 
                                      String mergeMethod, String oauthToken) {
        log.info("Merging PR #{} in {}/{} using method: {}", prNumber, owner, repo, 
                mergeMethod != null ? mergeMethod : "merge");

        try {
            Map<String, Object> mergeBody = new HashMap<>();
            if (commitTitle != null && !commitTitle.isBlank()) {
                mergeBody.put("commit_title", commitTitle);
            }
            if (commitMessage != null && !commitMessage.isBlank()) {
                mergeBody.put("commit_message", commitMessage);
            }
            if (mergeMethod != null && !mergeMethod.isBlank()) {
                mergeBody.put("merge_method", mergeMethod);
            }

            String bodyJson = objectMapper.writeValueAsString(mergeBody);

            String response = withAuth(
                    webClient.put()
                            .uri("/repos/{owner}/{repo}/pulls/{prNumber}/merge", owner, repo, prNumber)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("PR #{} merged successfully", prNumber);
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to merge PR #{}: {} - {}", prNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to merge PR #" + prNumber + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to merge PR #" + prNumber, e);
        }
    }

    /**
     * Closes a pull request without merging.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @return Updated PR as JsonNode
     */
    public JsonNode closePullRequest(String owner, String repo, int prNumber) {
        return closePullRequest(owner, repo, prNumber, null);
    }

    /**
     * Closes a pull request without merging with optional OAuth token.
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param oauthToken OAuth token for authentication
     * @return Updated PR as JsonNode
     */
    public JsonNode closePullRequest(String owner, String repo, int prNumber, String oauthToken) {
        log.info("Closing PR #{} in {}/{}", prNumber, owner, repo);

        try {
            Map<String, String> updateBody = Map.of("state", "closed");
            String bodyJson = objectMapper.writeValueAsString(updateBody);

            String response = withAuth(
                    webClient.patch()
                            .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("PR #{} closed successfully", prNumber);
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to close PR #{}: {} - {}", prNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to close PR #" + prNumber + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to close PR #" + prNumber, e);
        }
    }

    /**
     * Updates a pull request state (open/closed).
     * 
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @param state New state: "open" or "closed"
     * @param oauthToken OAuth token for authentication
     * @return Updated PR as JsonNode
     */
    public JsonNode updatePullRequestState(String owner, String repo, int prNumber, 
                                            String state, String oauthToken) {
        log.info("Updating PR #{} state to '{}' in {}/{}", prNumber, state, owner, repo);

        try {
            Map<String, String> updateBody = Map.of("state", state);
            String bodyJson = objectMapper.writeValueAsString(updateBody);

            String response = withAuth(
                    webClient.patch()
                            .uri("/repos/{owner}/{repo}/pulls/{prNumber}", owner, repo, prNumber)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(bodyJson),
                    oauthToken
            ).retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("PR #{} state updated to '{}'", prNumber, state);
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            log.error("Failed to update PR #{}: {} - {}", prNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update PR #" + prNumber + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update PR #" + prNumber, e);
        }
    }
}
