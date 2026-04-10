package com.helpagent.action.tools;

import com.helpagent.action.model.dto.ConflictPattern;
import com.helpagent.action.model.dto.SandboxResult;
import com.helpagent.action.rag.ConflictPatternStore;
import com.helpagent.action.service.GitHubApiService;
import com.helpagent.action.service.ReviewReportService;
import com.helpagent.action.service.SandboxService;
import com.helpagent.action.service.GitHubOAuthService;
import com.google.adk.tools.Annotations.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ADK FunctionTools for code review, sandbox validation, RAG pattern search,
 * and human review report generation.
 *
 * <p>These tools extend the agent's capabilities beyond merge conflict detection:
 * <ul>
 *   <li><b>validateCodeInSandbox</b> — Compile/lint code in a Docker container</li>
 *   <li><b>searchConflictPatterns</b> — Query RAG for similar past resolutions</li>
 *   <li><b>storeConflictPattern</b> — Save a successful resolution for future use</li>
 *   <li><b>generateHumanReviewReport</b> — Create a review.md and post it on the PR</li>
 * </ul>
 */
@Component
public class CodeReviewTools {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewTools.class);

    private final SandboxService sandboxService;
    private final ConflictPatternStore patternStore;
    private final ReviewReportService reviewReportService;
    private final GitHubApiService gitHubApiService;
    private final GitHubOAuthService oauthService;

    public CodeReviewTools(SandboxService sandboxService,
                           ConflictPatternStore patternStore,
                           ReviewReportService reviewReportService,
                           GitHubApiService gitHubApiService,
                           GitHubOAuthService oauthService) {
        this.sandboxService = sandboxService;
        this.patternStore = patternStore;
        this.reviewReportService = reviewReportService;
        this.gitHubApiService = gitHubApiService;
        this.oauthService = oauthService;
    }

    // ─── Sandbox Validation ───

    /**
     * Validates code by compiling or linting it in an isolated Docker container.
     * Use this BEFORE committing resolved files to catch syntax errors or compilation failures.
     *
     * Returns a result with success/failure status, any error output, and the language detected.
     * If validation fails, you should fix the code and try again.
     */
    public Map<String, Object> validateCodeInSandbox(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "File path (e.g., 'src/main/java/App.java')") String filePath,
            @Schema(description = "The code content to validate") String content,
            @Schema(description = "Programming language (java, python, javascript, typescript). Leave empty to auto-detect from file extension.") String language) {

        log.info("Tool: validateCodeInSandbox({}/{}, {}, lang={})", owner, repo, filePath, language);

        try {
            SandboxResult result = sandboxService.validate(filePath, content, language);

            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isSuccess() ? "success" : "failure");
            response.put("valid", result.isSuccess());
            response.put("language", result.getLanguage());
            response.put("filename", result.getFilename());
            response.put("exitCode", result.getExitCode());
            response.put("durationMs", result.getDurationMs());

            if (!result.isSuccess()) {
                response.put("errors", result.getStderr());
                response.put("stdout", result.getStdout());
                response.put("message", "Code validation failed. Please fix the errors and try again.");
            } else {
                response.put("message", "Code validation passed successfully.");
            }

            return response;

        } catch (Exception e) {
            log.error("Sandbox validation error for {}: {}", filePath, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("valid", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    // ─── RAG Pattern Search ───

    /**
     * Searches the RAG store for similar past conflict resolution patterns.
     * Use this BEFORE generating a resolution to see if similar conflicts have been
     * resolved before.
     *
     * Returns a list of similar patterns with their resolution strategies. Use these
     * as guidance when crafting your own resolution.
     */
    public Map<String, Object> searchConflictPatterns(
            @Schema(description = "Natural-language description of the conflict (e.g., 'import ordering conflict in Java utility class')") String conflictDescription) {

        log.info("Tool: searchConflictPatterns('{}')", truncate(conflictDescription, 80));

        try {
            List<ConflictPattern> patterns = patternStore.findSimilarPatterns(conflictDescription);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("totalPatterns", patternStore.getPatternCount());
            response.put("matchCount", patterns.size());

            List<Map<String, String>> patternList = patterns.stream()
                    .map(p -> {
                        Map<String, String> pm = new HashMap<>();
                        pm.put("id", p.getId());
                        pm.put("conflictDescription", p.getConflictDescription());
                        pm.put("filePattern", p.getFilePattern());
                        pm.put("resolutionStrategy", p.getResolutionStrategy());
                        pm.put("repository", p.getRepository());
                        pm.put("resolvedContent", truncate(p.getResolvedContent(), 2000));
                        return pm;
                    })
                    .collect(Collectors.toList());

            response.put("patterns", patternList);

            if (patterns.isEmpty()) {
                response.put("message", "No similar patterns found. Proceed with independent analysis.");
            } else {
                response.put("message", "Found " + patterns.size() + " similar past resolution(s). " +
                        "Use them as guidance for your resolution strategy.");
            }

            return response;

        } catch (Exception e) {
            log.error("RAG pattern search error: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            response.put("matchCount", 0);
            return response;
        }
    }

    // ─── RAG Pattern Storage ───

    /**
     * Stores a successful conflict resolution pattern in the RAG store.
     * Call this AFTER successfully resolving and committing a merge conflict
     * so the pattern can be used for similar future conflicts.
     */
    public Map<String, String> storeConflictPattern(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Description of the conflict type (e.g., 'concurrent changes to application.yml database config')") String conflictDescription,
            @Schema(description = "File pattern (e.g., '*.java', 'pom.xml', 'application.yml')") String filePattern,
            @Schema(description = "How the conflict was resolved — strategy description") String resolutionStrategy,
            @Schema(description = "The resolved file content (or a representative snippet)") String resolvedContent) {

        log.info("Tool: storeConflictPattern({}/{}, '{}', '{}')",
                owner, repo, truncate(conflictDescription, 60), filePattern);

        try {
            ConflictPattern pattern = new ConflictPattern(
                    conflictDescription, filePattern, resolutionStrategy,
                    truncate(resolvedContent, 5000),  // Limit stored content size
                    owner + "/" + repo
            );

            patternStore.storePattern(pattern);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("patternId", pattern.getId());
            response.put("totalPatterns", String.valueOf(patternStore.getPatternCount()));
            response.put("message", "Conflict resolution pattern stored successfully. " +
                    "It will be available for similar future conflicts.");
            return response;

        } catch (Exception e) {
            log.error("Failed to store conflict pattern: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }

    // ─── Human Review Report ───

    /**
     * Generates a detailed review.md report for human review and posts it on the PR.
     * Call this when automated resolution has FAILED after both attempts.
     *
     * The report contains conflict details, what was tried, sandbox results,
     * and suggestions for manual resolution.
     */
    public Map<String, String> generateHumanReviewReport(
            @Schema(description = "Repository owner") String owner,
            @Schema(description = "Repository name") String repo,
            @Schema(description = "Pull request number") int prNumber,
            @Schema(description = "Summary of what conflicts were found") String conflictSummary,
            @Schema(description = "Detailed description of what was attempted in resolution (include both iteration details)") String attemptDetails,
            @Schema(description = "Comma-separated list of conflicting filenames") String conflictFilesStr,
            @Schema(description = "Suggestions for the human reviewer on how to resolve these conflicts") String suggestions) {

        log.info("Tool: generateHumanReviewReport({}/{}, PR#{})", owner, repo, prNumber);

        try {
            List<String> conflictFiles = conflictFilesStr != null
                    ? Arrays.asList(conflictFilesStr.split(","))
                    : List.of();

            // Parse attempt details into a list (split by newline markers if provided)
            List<String> attempts = attemptDetails != null
                    ? Arrays.asList(attemptDetails.split("\\|\\|"))
                    : List.of("No details provided.");

            String report = reviewReportService.generateReviewReport(
                    owner, repo, prNumber, "PR #" + prNumber,
                    "base", "feature",
                    conflictSummary,
                    attempts,
                    conflictFiles,
                    List.of(),  // Sandbox results tracked separately
                    List.of(),  // RAG patterns tracked separately
                    suggestions
            );

            // Post the report as a PR comment
            String oauthToken = getOAuthToken();
            reviewReportService.postReviewAsComment(owner, repo, prNumber, report, oauthToken);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Review report generated and posted on PR #" + prNumber);
            response.put("reportLength", String.valueOf(report.length()));
            return response;

        } catch (Exception e) {
            log.error("Failed to generate review report: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("error", e.getMessage());
            return response;
        }
    }

    // ─── Helpers ───

    private String getOAuthToken() {
        String oauthUserId = OAuthContext.getOAuthUserId();
        if (oauthUserId == null || oauthUserId.isBlank()) return null;
        return oauthService.getAccessToken(oauthUserId);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
