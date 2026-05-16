package com.helpagent.action.repository;

import com.helpagent.action.model.entity.AgentWorkflowEntity;
import com.helpagent.action.model.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflowEntity, String> {

    List<AgentWorkflowEntity> findByStatusInOrderByStartedAtDesc(List<WorkflowStatus> statuses);

    List<AgentWorkflowEntity> findTop50ByOrderByStartedAtDesc();

    List<AgentWorkflowEntity> findByOwnerAndRepoOrderByStartedAtDesc(String owner, String repo);

    List<AgentWorkflowEntity> findByOauthUserIdOrderByStartedAtDesc(String oauthUserId);

    long countByStatus(WorkflowStatus status);
}
