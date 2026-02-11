package com.helpagent.action.model;

/**
 * Represents a single file's diff from a pull request.
 */
public class FileDiff {

    private String filename;
    private String status; // added, removed, modified, renamed
    private int additions;
    private int deletions;
    private int changes;
    private String patch; // The actual diff/patch content
    private String previousFilename; // For renames

    public FileDiff() {}

    public FileDiff(String filename, String status, String patch) {
        this.filename = filename;
        this.status = status;
        this.patch = patch;
    }

    // ── Getters & Setters ──

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAdditions() { return additions; }
    public void setAdditions(int additions) { this.additions = additions; }

    public int getDeletions() { return deletions; }
    public void setDeletions(int deletions) { this.deletions = deletions; }

    public int getChanges() { return changes; }
    public void setChanges(int changes) { this.changes = changes; }

    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }

    public String getPreviousFilename() { return previousFilename; }
    public void setPreviousFilename(String previousFilename) { this.previousFilename = previousFilename; }

    /**
     * Checks if this is a binary file (no patch available).
     */
    public boolean isBinary() {
        return patch == null || patch.isBlank();
    }

    @Override
    public String toString() {
        return "FileDiff{filename='%s', status='%s', +%d/-%d}"
                .formatted(filename, status, additions, deletions);
    }
}
