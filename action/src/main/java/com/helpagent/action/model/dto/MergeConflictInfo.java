package com.helpagent.action.model.dto;

import java.util.List;

/**
 * Holds information about merge conflicts detected between two branches.
 */
public class MergeConflictInfo {

    private String baseBranch;
    private String featureBranch;
    private boolean hasConflicts;
    private List<ConflictFile> conflictFiles;
    private String rawDiff;

    public MergeConflictInfo() {}

    public MergeConflictInfo(String baseBranch, String featureBranch, boolean hasConflicts,
                              List<ConflictFile> conflictFiles, String rawDiff) {
        this.baseBranch = baseBranch;
        this.featureBranch = featureBranch;
        this.hasConflicts = hasConflicts;
        this.conflictFiles = conflictFiles;
        this.rawDiff = rawDiff;
    }

    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }
    public String getFeatureBranch() { return featureBranch; }
    public void setFeatureBranch(String featureBranch) { this.featureBranch = featureBranch; }
    public boolean isHasConflicts() { return hasConflicts; }
    public void setHasConflicts(boolean hasConflicts) { this.hasConflicts = hasConflicts; }
    public List<ConflictFile> getConflictFiles() { return conflictFiles; }
    public void setConflictFiles(List<ConflictFile> conflictFiles) { this.conflictFiles = conflictFiles; }
    public String getRawDiff() { return rawDiff; }
    public void setRawDiff(String rawDiff) { this.rawDiff = rawDiff; }

    /**
     * Represents a single file with conflict information.
     */
    public static class ConflictFile {
        private String filename;
        private String baseContent;
        private String featureContent;
        private String conflictDiff;
        private String status; // "conflicted", "modified", "added", "deleted"

        public ConflictFile() {}

        public ConflictFile(String filename, String baseContent, String featureContent,
                           String conflictDiff, String status) {
            this.filename = filename;
            this.baseContent = baseContent;
            this.featureContent = featureContent;
            this.conflictDiff = conflictDiff;
            this.status = status;
        }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getBaseContent() { return baseContent; }
        public void setBaseContent(String baseContent) { this.baseContent = baseContent; }
        public String getFeatureContent() { return featureContent; }
        public void setFeatureContent(String featureContent) { this.featureContent = featureContent; }
        public String getConflictDiff() { return conflictDiff; }
        public void setConflictDiff(String conflictDiff) { this.conflictDiff = conflictDiff; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return String.format("ConflictFile{filename='%s', status='%s'}", filename, status);
        }
    }

    @Override
    public String toString() {
        return String.format("MergeConflictInfo{base='%s', feature='%s', hasConflicts=%s, files=%d}",
                baseBranch, featureBranch, hasConflicts,
                conflictFiles != null ? conflictFiles.size() : 0);
    }
}
