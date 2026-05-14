package com.enterprise.copilot.entity;

import com.enterprise.copilot.enums.WorkflowStatus;
import com.enterprise.copilot.enums.DecisionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflows", indexes = {
        @Index(name = "idx_wf_status", columnList = "status"),
        @Index(name = "idx_wf_created_by", columnList = "created_by_id"),
        @Index(name = "idx_wf_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===============================
    // DOCUMENT INFO
    // ===============================
    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "document_type")
    private String documentType;

    // ===============================
    // STATUS & DECISION
    // ===============================
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type")
    private DecisionType decisionType;

    // ===============================
    // AI + EXTRACTION
    // ===============================
    @Column(name = "extracted_data", columnDefinition = "TEXT")
    private String extractedData;

    @Column(name = "ai_recommendation", columnDefinition = "TEXT")
    private String aiRecommendation;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    // ===============================
    // EXPLAINABLE AI
    // ===============================
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    // ===============================
    // RAG - FIXED: insertable=false, updatable=false
    // Hibernate must NOT touch this column — it is written
    // exclusively via the native updateEmbedding() query which
    // correctly uses CAST(:embedding AS vector).
    // ===============================
    @Column(name = "embedding", columnDefinition = "vector(768)",
            insertable = false, updatable = false)
    private String embedding;

    // ===============================
    // REVIEW SYSTEM
    // ===============================
    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ===============================
    // RELATIONS
    // ===============================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    // ===============================
    // AUDIT
    // ===============================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
