package com.helpagent.action.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity tracking the overall status of each merge analysis workflow run.
 * One row per PR analysis triggered by webhook or manual request.
 */
@Entity
@Table(name = "agent_workflows")
public class AgentWorkflowEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "owner", nullable = false)
    private String owner;

    @Column(name = "repo", nullable = false)
    private String repo;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "pr_title", length = 500)
    private String prTitle;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "feature_branch")
    private String featureBranch;

    @Column(name = "oauth_user_id")
    private String oauthUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkflowStatus status;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "agent_response", columnDefinition = "TEXT")
    private String agentResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_category", length = 50)
    private String errorCategory;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AgentWorkflowEntity() {}

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    public int getPrNumber() { return prNumber; }
    public void setPrNumber(int prNumber) { this.prNumber = prNumber; }

    public String getPrTitle() { return prTitle; }
    public void setPrTitle(String prTitle) { this.prTitle = prTitle; }

    public String getBaseBranch() { return baseBranch; }
    public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }

    public String getFeatureBranch() { return featureBranch; }
    public void setFeatureBranch(String featureBranch) { this.featureBranch = featureBranch; }

    public String getOauthUserId() { return oauthUserId; }
    public void setOauthUserId(String oauthUserId) { this.oauthUserId = oauthUserId; }

    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public String getAgentResponse() { return agentResponse; }
    public void setAgentResponse(String agentResponse) { this.agentResponse = agentResponse; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
