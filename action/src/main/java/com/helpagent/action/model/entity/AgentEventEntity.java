package com.helpagent.action.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity persisting every ADK Event for live workflow tracking and history replay.
 * Each event maps to one row, linked to a parent workflow via workflowId.
 */
@Entity
@Table(name = "agent_events", indexes = {
    @Index(name = "idx_events_workflow", columnList = "workflow_id")
})
public class AgentEventEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "author")
    private String author;

    @Column(name = "event_type", length = 30)
    private String eventType;

    @Column(name = "content_summary", length = 500)
    private String contentSummary;

    @Column(name = "content_full", columnDefinition = "TEXT")
    private String contentFull;

    @Column(name = "final_response")
    private boolean finalResponse;

    @Column(name = "escalated")
    private boolean escalated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    public AgentEventEntity() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getContentSummary() { return contentSummary; }
    public void setContentSummary(String contentSummary) { this.contentSummary = contentSummary; }

    public String getContentFull() { return contentFull; }
    public void setContentFull(String contentFull) { this.contentFull = contentFull; }

    public boolean isFinalResponse() { return finalResponse; }
    public void setFinalResponse(boolean finalResponse) { this.finalResponse = finalResponse; }

    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}
