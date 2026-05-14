package com.enterprise.copilot.Dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO returned by GET /api/v1/workflows/{id}/rag-insights
 * Previously was incorrectly named "RagInsight" — fixed to RagInsightDto.
 */
@Data
@Builder
public class RagInsightDto {
    private Long   workflowId;
    private String documentName;
    private String matchedVendor;
    private Double matchedAmount;
    private String matchedDecision;
    private Double confidenceScore;
    private String summary;
}
