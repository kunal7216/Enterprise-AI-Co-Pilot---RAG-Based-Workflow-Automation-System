package com.enterprise.copilot.Dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response for GET /dashboard/insights
 * Uses ChartPointDto (fixed from old chartPoint lowercase class).
 */
@Data
@Builder
public class DashboardInsightsResponse {
    private WorkflowStats        stats;
    private List<ChartPointDto>  statusChart;    // ← FIXED (was List<chartPoint>)
    private List<ChartPointDto>  decisionChart;  // ← FIXED (was List<chartPoint>)
}
