package com.helpagent.action.repository;

import com.helpagent.action.model.entity.AgentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentEventRepository extends JpaRepository<AgentEventEntity, String> {

    List<AgentEventEntity> findByWorkflowIdOrderBySequenceNumberAsc(String workflowId);

    AgentEventEntity findTopByWorkflowIdOrderBySequenceNumberDesc(String workflowId);
}
