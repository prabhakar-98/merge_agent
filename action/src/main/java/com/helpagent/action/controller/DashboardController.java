package com.helpagent.action.controller;

import com.helpagent.action.model.entity.AgentEventEntity;
import com.helpagent.action.model.entity.AgentWorkflowEntity;
import com.helpagent.action.model.entity.WorkflowStatus;
import com.helpagent.action.repository.AgentEventRepository;
import com.helpagent.action.repository.AgentWorkflowRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller serving the frontend dashboard with workflow status,
 * event history, and aggregate statistics.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AgentWorkflowRepository workflowRepository;
    private final AgentEventRepository eventRepository;

    public DashboardController(AgentWorkflowRepository workflowRepository,
                                AgentEventRepository eventRepository) {
        this.workflowRepository = workflowRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Get active workflows (QUEUED, RUNNING, HUMAN_IN_LOOP).
     */
    @GetMapping("/workflows/active")
    public ResponseEntity<List<AgentWorkflowEntity>> getActiveWorkflows() {
        List<AgentWorkflowEntity> active = workflowRepository.findByStatusInOrderByStartedAtDesc(
                List.of(WorkflowStatus.QUEUED, WorkflowStatus.RUNNING, WorkflowStatus.HUMAN_IN_LOOP)
        );
        return ResponseEntity.ok(active);
    }

    /**
     * Get recent workflow history (last 50, all statuses).
     */
    @GetMapping("/workflows/history")
    public ResponseEntity<List<AgentWorkflowEntity>> getWorkflowHistory() {
        List<AgentWorkflowEntity> history = workflowRepository.findTop50ByOrderByStartedAtDesc();
        return ResponseEntity.ok(history);
    }

    /**
     * Get a single workflow by ID.
     */
    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        return workflowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all events for a specific workflow.
     */
    @GetMapping("/workflows/{id}/events")
    public ResponseEntity<List<AgentEventEntity>> getWorkflowEvents(@PathVariable String id) {
        List<AgentEventEntity> events = eventRepository.findByWorkflowIdOrderBySequenceNumberAsc(id);
        return ResponseEntity.ok(events);
    }

    /**
     * Get aggregate dashboard statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = workflowRepository.count();
        long completed = workflowRepository.countByStatus(WorkflowStatus.COMPLETED);
        long failed = workflowRepository.countByStatus(WorkflowStatus.FAILED);
        long escalated = workflowRepository.countByStatus(WorkflowStatus.ESCALATED);
        long humanInLoop = workflowRepository.countByStatus(WorkflowStatus.HUMAN_IN_LOOP);
        long running = workflowRepository.countByStatus(WorkflowStatus.RUNNING);
        long queued = workflowRepository.countByStatus(WorkflowStatus.QUEUED);

        return ResponseEntity.ok(Map.of(
                "total", total,
                "completed", completed,
                "failed", failed,
                "escalated", escalated,
                "humanInLoop", humanInLoop,
                "running", running,
                "queued", queued,
                "active", running + queued + humanInLoop
        ));
    }
}
