package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowStats {

    private long totalWorkflows;
    private long pendingWorkflows;
    private long completedWorkflows;
    private long rejectedWorkflows;
    private long escalatedWorkflows;

    private double avgConfidenceScore;
    private double avgProcessingTimeSeconds;
    private double automationRate;
}