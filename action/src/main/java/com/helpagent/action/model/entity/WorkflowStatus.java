package com.helpagent.action.model.entity;

/**
 * Represents the lifecycle status of an agent merge analysis workflow.
 */
public enum WorkflowStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    ESCALATED,
    HUMAN_IN_LOOP
}
