package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowResponse {

    private Long id;
    private String status;
    private String decisionType;

    private String documentName;
    private String documentType;

    // ─── AI Results ───────────────────────────────────────────────
    private String extractedData;
    private Double confidenceScore;
    private String aiRecommendation;
    private Long processingTimeMs;

    // Explainable AI
    private String decisionReason;

    // ─── Human Review ─────────────────────────────────────────────
    private String reviewComments;
    private String reviewedAt;
    private String reviewedByUsername;

    // ─── Audit ────────────────────────────────────────────────────
    private String createdByUsername;
    private String createdAt;
    private String updatedAt;

    // RAG insights for frontend
    private List<RagInsightDto> ragInsights;
}