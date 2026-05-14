package com.enterprise.copilot.entity;

import com.enterprise.copilot.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_history", indexes = {
        @Index(name = "idx_hist_wf_id", columnList = "workflow_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private WorkflowStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private WorkflowStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}