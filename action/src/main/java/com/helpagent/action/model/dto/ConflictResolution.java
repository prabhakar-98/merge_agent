package com.helpagent.action.model.dto;

import java.util.List;

/**
 * Holds the LLM-generated conflict resolution suggestion.
 */
public class ConflictResolution {

    private String summary;
    private String reasoning;
    private List<ResolvedFile> resolvedFiles;
    private String resolvedBranchName;
    private boolean success;

    public ConflictResolution() {}

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public List<ResolvedFile> getResolvedFiles() { return resolvedFiles; }
    public void setResolvedFiles(List<ResolvedFile> resolvedFiles) { this.resolvedFiles = resolvedFiles; }
    public String getResolvedBranchName() { return resolvedBranchName; }
    public void setResolvedBranchName(String resolvedBranchName) { this.resolvedBranchName = resolvedBranchName; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    /**
     * Represents a resolved file with suggested content.
     */
    public static class ResolvedFile {
        private String filename;
        private String resolvedContent;
        private String explanation;

        public ResolvedFile() {}

        public ResolvedFile(String filename, String resolvedContent, String explanation) {
            this.filename = filename;
            this.resolvedContent = resolvedContent;
            this.explanation = explanation;
        }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getResolvedContent() { return resolvedContent; }
        public void setResolvedContent(String resolvedContent) { this.resolvedContent = resolvedContent; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }
}
