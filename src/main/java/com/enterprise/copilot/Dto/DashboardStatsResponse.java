package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatsResponse {

    private long totalWorkflows;
    private long pendingWorkflows;
    private long completedWorkflows;
    private long rejectedWorkflows;
    private long escalatedWorkflows;

    // Performance KPIs from B.Tech report
    private double avgConfidenceScore;        // as decimal e.g. 0.92
    private double avgProcessingTimeSeconds;
    private double automationRate;            // % auto-approved without human
}