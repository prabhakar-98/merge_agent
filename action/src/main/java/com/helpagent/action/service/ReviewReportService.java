package com.helpagent.action.service;

import com.helpagent.action.model.dto.SandboxResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for generating structured review reports (review.md format).
 *
 * <p>When the agent cannot resolve merge conflicts after 2 iterations,
 * this service generates a detailed Markdown report summarizing:
 * <ul>
 *   <li>PR details and conflict overview</li>
 *   <li>What was attempted in each iteration</li>
 *   <li>Sandbox validation results</li>
 *   <li>RAG patterns that were consulted</li>
 *   <li>Recommended manual actions for the human reviewer</li>
 * </ul>
 *
 * <p>The report is posted as a PR comment (and optionally as a GitHub Gist).
 */
@Service
public class ReviewReportService {

    private static final Logger log = LoggerFactory.getLogger(ReviewReportService.class);

    private final GitHubApiService gitHubApiService;

    public ReviewReportService(GitHubApiService gitHubApiService) {
        this.gitHubApiService = gitHubApiService;
    }

    /**
     * Generates a full review.md Markdown report.
     *
     * @param owner            repository owner
     * @param repo             repository name
     * @param prNumber         pull request number
     * @param prTitle          PR title
     * @param baseBranch       base branch name
     * @param featureBranch    feature branch name
     * @param conflictSummary  summary of detected conflicts
     * @param attemptDetails   details of each resolution attempt
     * @param conflictFiles    list of conflicting file names
     * @param sandboxResults   sandbox validation results (may be empty)
     * @param ragPatternsUsed  descriptions of RAG patterns that were consulted
     * @param suggestions      human-readable suggestions for manual resolution
     * @return the complete Markdown report
     */
    public String generateReviewReport(
            String owner, String repo, int prNumber, String prTitle,
            String baseBranch, String featureBranch,
            String conflictSummary,
            List<String> attemptDetails,
            List<String> conflictFiles,
            List<SandboxResult> sandboxResults,
            List<String> ragPatternsUsed,
            String suggestions) {

        StringBuilder report = new StringBuilder();

        // ── Header ──
        report.append("# 📋 Merge Help Agent — Review Report\n\n");
        report.append("> **Automated resolution failed after 2 attempts. Manual review required.**\n\n");
        report.append("---\n\n");

        // ── PR Details ──
        report.append("## 📌 Pull Request Details\n\n");
        report.append(String.format("| Field | Value |\n|-------|-------|\n"));
        report.append(String.format("| **Repository** | `%s/%s` |\n", owner, repo));
        report.append(String.format("| **PR** | #%d — %s |\n", prNumber, prTitle));
        report.append(String.format("| **Base Branch** | `%s` |\n", baseBranch));
        report.append(String.format("| **Feature Branch** | `%s` |\n", featureBranch));
        report.append(String.format("| **Report Generated** | %s |\n", Instant.now().toString()));
        report.append("\n");

        // ── Conflict Summary ──
        report.append("## 🔍 Conflict Summary\n\n");
        report.append(conflictSummary != null ? conflictSummary : "_No conflict summary available._");
        report.append("\n\n");

        // ── Conflicting Files ──
        if (conflictFiles != null && !conflictFiles.isEmpty()) {
            report.append("### Conflicting Files\n\n");
            for (String file : conflictFiles) {
                report.append(String.format("- `%s`\n", file));
            }
            report.append("\n");
        }

        // ── Resolution Attempts ──
        report.append("## 🔄 Resolution Attempts\n\n");
        if (attemptDetails != null && !attemptDetails.isEmpty()) {
            for (int i = 0; i < attemptDetails.size(); i++) {
                report.append(String.format("### Attempt %d\n\n", i + 1));
                report.append(attemptDetails.get(i));
                report.append("\n\n");
            }
        } else {
            report.append("_No attempt details recorded._\n\n");
        }

        // ── Sandbox Validation Results ──
        if (sandboxResults != null && !sandboxResults.isEmpty()) {
            report.append("## 🧪 Sandbox Validation Results\n\n");
            report.append("| File | Language | Status | Exit Code | Duration |\n");
            report.append("|------|----------|--------|-----------|----------|\n");
            for (SandboxResult result : sandboxResults) {
                String status = result.isSuccess() ? "✅ Pass" : "❌ Fail";
                report.append(String.format("| `%s` | %s | %s | %d | %dms |\n",
                        result.getFilename(), result.getLanguage(), status,
                        result.getExitCode(), result.getDurationMs()));
            }
            report.append("\n");

            // Add error details for failures
            List<SandboxResult> failures = sandboxResults.stream()
                    .filter(r -> !r.isSuccess())
                    .toList();
            if (!failures.isEmpty()) {
                report.append("### Sandbox Errors\n\n");
                for (SandboxResult failure : failures) {
                    report.append(String.format("**`%s`**:\n```\n%s\n```\n\n",
                            failure.getFilename(), failure.getStderr()));
                }
            }
        }

        // ── RAG Patterns Consulted ──
        if (ragPatternsUsed != null && !ragPatternsUsed.isEmpty()) {
            report.append("## 📚 RAG Patterns Consulted\n\n");
            report.append("The following similar past resolution patterns were retrieved:\n\n");
            for (int i = 0; i < ragPatternsUsed.size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, ragPatternsUsed.get(i)));
            }
            report.append("\n");
        }

        // ── Suggestions ──
        report.append("## 💡 Suggested Manual Approach\n\n");
        report.append(suggestions != null ? suggestions : "_No specific suggestions. Please review conflicts manually._");
        report.append("\n\n");

        // ── Footer ──
        report.append("---\n\n");
        report.append("_This report was generated by the **Merge Help Agent**. ");
        report.append("Please resolve the remaining conflicts manually and update the PR._\n");

        return report.toString();
    }

    /**
     * Posts the review report as a comment on the PR.
     *
     * @param owner      repository owner
     * @param repo       repository name
     * @param prNumber   pull request number
     * @param report     the Markdown report content
     * @param oauthToken OAuth token (optional)
     */
    public void postReviewAsComment(String owner, String repo, int prNumber,
                                     String report, String oauthToken) {
        log.info("Posting review report as PR comment on {}/{} PR#{}", owner, repo, prNumber);

        try {
            // GitHub comments have a 65536 character limit
            String commentBody = report;
            if (commentBody.length() > 60000) {
                commentBody = commentBody.substring(0, 60000) +
                        "\n\n---\n_⚠️ Report truncated (exceeded character limit)._\n";
            }

            gitHubApiService.commentOnPR(owner, repo, prNumber, commentBody, oauthToken);
            log.info("Review report posted successfully on PR #{}", prNumber);
        } catch (Exception e) {
            log.error("Failed to post review report on PR #{}: {}", prNumber, e.getMessage(), e);
        }
    }

    /**
     * Generates a concise summary suitable for tool return values.
     */
    public Map<String, String> generateReportSummary(
            String owner, String repo, int prNumber,
            String conflictSummary, List<String> conflictFiles) {

        String report = generateReviewReport(
                owner, repo, prNumber, "PR #" + prNumber,
                "unknown", "unknown",
                conflictSummary,
                List.of("Details available in the PR comment."),
                conflictFiles,
                List.of(),
                List.of(),
                "Please review the conflicting files manually."
        );

        return Map.of(
                "status", "review_report_generated",
                "reportLength", String.valueOf(report.length()),
                "conflictingFiles", String.join(", ", conflictFiles != null ? conflictFiles : List.of()),
                "report", report
        );
    }
}
