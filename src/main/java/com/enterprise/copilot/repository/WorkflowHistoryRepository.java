package com.enterprise.copilot.repository;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.entity.WorkflowHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, Long> {

    // Most recent first for timeline display
    List<WorkflowHistory> findByWorkflowOrderByChangedAtDesc(Workflow workflow);

    long countByWorkflow(Workflow workflow);
}