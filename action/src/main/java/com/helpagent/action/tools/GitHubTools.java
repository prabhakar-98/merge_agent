package com.helpagent.action.tools;

import com.google.adk.tools.Annotations.Schema;
import com.helpagent.action.model.FileDiff;
import com.helpagent.action.service.GitHubApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GitHub MCP Tools — Function tools exposed to the ADK agent.
 *
 * These tools give the AI agent the ability to interact with GitHub:
 * fetch PR diffs, file contents, and PR metadata. The agent uses these
 * tools during the code review process to gather context.
 */
public class GitHubTools {

    private static final Logger log = LoggerFactory.getLogger(GitHubTools.class);

    private final GitHubApiService gitHubApiService;

    public GitHubTools(GitHubApiService gitHubApiService) {
        this.gitHubApiService = gitHubApiService;
    }

    /**
     * Fetches the list of changed files in a pull request along with their diffs/patches.
     */
    @Schema(description = "Fetch the list of files changed in a GitHub pull request with their diffs/patches")
    public Map<String, Object> getPullRequestFiles(
            @Schema(name = "repoFullName", description = "Full repository name in owner/repo format") String repoFullName,
            @Schema(name = "prNumber", description = "Pull request number") int prNumber) {

        log.info("Tool called: getPullRequestFiles({}, {})", repoFullName, prNumber);
        try {
            List<FileDiff> files = gitHubApiService.getPullRequestFiles(repoFullName, prNumber);

            List<Map<String, Object>> fileList = files.stream()
                    .map(f -> {
                        Map<String, Object> fileMap = new HashMap<>();
                        fileMap.put("filename", f.getFilename());
                        fileMap.put("status", f.getStatus());
                        fileMap.put("additions", f.getAdditions());
                        fileMap.put("deletions", f.getDeletions());
                        fileMap.put("changes", f.getChanges());
                        fileMap.put("patch", f.getPatch() != null ? f.getPatch() : "BINARY_FILE");
                        if (f.getPreviousFilename() != null) {
                            fileMap.put("previous_filename", f.getPreviousFilename());
                        }
                        return fileMap;
                    })
                    .collect(Collectors.toList());

            return Map.of(
                    "status", "success",
                    "total_files", files.size(),
                    "files", fileList
            );
        } catch (Exception e) {
            log.error("Error fetching PR files", e);
            return Map.of("status", "error", "error_message", e.getMessage());
        }
    }

    /**
     * Fetches the full raw unified diff for a pull request.
     */
    @Schema(description = "Fetch the full raw unified diff for a GitHub pull request")
    public Map<String, Object> getPullRequestDiff(
            @Schema(name = "repoFullName", description = "Full repository name in owner/repo format") String repoFullName,
            @Schema(name = "prNumber", description = "Pull request number") int prNumber) {

        log.info("Tool called: getPullRequestDiff({}, {})", repoFullName, prNumber);
        try {
            String diff = gitHubApiService.getPullRequestDiff(repoFullName, prNumber);
            return Map.of("status", "success", "diff", diff);
        } catch (Exception e) {
            log.error("Error fetching PR diff", e);
            return Map.of("status", "error", "error_message", e.getMessage());
        }
    }

    /**
     * Fetches the content of a specific file at a given Git ref (branch or commit SHA).
     */
    @Schema(description = "Fetch the content of a file from a GitHub repository at a specific ref (branch or commit)")
    public Map<String, Object> getFileContent(
            @Schema(name = "repoFullName", description = "Full repository name in owner/repo format") String repoFullName,
            @Schema(name = "filePath", description = "Path to the file within the repository") String filePath,
            @Schema(name = "ref", description = "Git ref - branch name or commit SHA") String ref) {

        log.info("Tool called: getFileContent({}, {}, {})", repoFullName, filePath, ref);
        try {
            String content = gitHubApiService.getFileContent(repoFullName, filePath, ref);
            if (content == null) {
                return Map.of("status", "error", "error_message", "File not found or inaccessible");
            }
            return Map.of("status", "success", "content", content, "path", filePath);
        } catch (Exception e) {
            log.error("Error fetching file content", e);
            return Map.of("status", "error", "error_message", e.getMessage());
        }
    }

    /**
     * Fetches pull request metadata (title, description, author, base/head branches).
     */
    @Schema(description = "Fetch pull request details including title, description, author, and branch information")
    public Map<String, Object> getPullRequestDetails(
            @Schema(name = "repoFullName", description = "Full repository name in owner/repo format") String repoFullName,
            @Schema(name = "prNumber", description = "Pull request number") int prNumber) {

        log.info("Tool called: getPullRequestDetails({}, {})", repoFullName, prNumber);
        try {
            Map<String, Object> prDetails = gitHubApiService.getPullRequestDetails(repoFullName, prNumber);

            // Extract relevant fields for the agent
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("title", prDetails.getOrDefault("title", ""));
            result.put("body", prDetails.getOrDefault("body", ""));
            result.put("state", prDetails.getOrDefault("state", ""));
            result.put("changed_files", prDetails.getOrDefault("changed_files", 0));
            result.put("additions", prDetails.getOrDefault("additions", 0));
            result.put("deletions", prDetails.getOrDefault("deletions", 0));

            // Extract user info
            if (prDetails.get("user") instanceof Map<?, ?> user) {
                result.put("author", user.get("login") != null ? user.get("login") : "unknown");
            }

            // Extract head/base info
            if (prDetails.get("head") instanceof Map<?, ?> head) {
                result.put("head_ref", head.get("ref") != null ? head.get("ref") : "");
                result.put("head_sha", head.get("sha") != null ? head.get("sha") : "");
            }
            if (prDetails.get("base") instanceof Map<?, ?> base) {
                result.put("base_ref", base.get("ref") != null ? base.get("ref") : "");
            }

            return result;
        } catch (Exception e) {
            log.error("Error fetching PR details", e);
            return Map.of("status", "error", "error_message", e.getMessage());
        }
    }
}
