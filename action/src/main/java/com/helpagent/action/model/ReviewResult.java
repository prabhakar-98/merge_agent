package com.helpagent.action.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the complete result of an AI code review on a pull request.
 */
public class ReviewResult {

    public enum ReviewVerdict {
        APPROVE,
        REQUEST_CHANGES,
        COMMENT
    }

    private String repoFullName;
    private int pullRequestNumber;
    private String commitSha;
    private ReviewVerdict verdict;
    private String summary;
    private List<ReviewComment> comments;
    private Instant reviewedAt;
    private long durationMs;

    public ReviewResult() {
        this.comments = new ArrayList<>();
        this.reviewedAt = Instant.now();
    }

    // ── Getters & Setters ──

    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }

    public int getPullRequestNumber() { return pullRequestNumber; }
    public void setPullRequestNumber(int pullRequestNumber) { this.pullRequestNumber = pullRequestNumber; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public ReviewVerdict getVerdict() { return verdict; }
    public void setVerdict(ReviewVerdict verdict) { this.verdict = verdict; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<ReviewComment> getComments() { return comments; }
    public void setComments(List<ReviewComment> comments) { this.comments = comments; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public void addComment(ReviewComment comment) {
        this.comments.add(comment);
    }

    /**
     * Maps the verdict to GitHub's review event format.
     */
    public String toGitHubEvent() {
        return switch (verdict) {
            case APPROVE -> "APPROVE";
            case REQUEST_CHANGES -> "REQUEST_CHANGES";
            case COMMENT -> "COMMENT";
        };
    }

    @Override
    public String toString() {
        return "ReviewResult{repo='%s', pr=#%d, verdict=%s, comments=%d}"
                .formatted(repoFullName, pullRequestNumber, verdict, comments.size());
    }
}
