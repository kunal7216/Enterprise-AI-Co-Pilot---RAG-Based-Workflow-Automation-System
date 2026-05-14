package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowHistoryResponse {

    private Long id;
    private String fromStatus;
    private String toStatus;
    private String changedByUsername;
    private String notes;
    private String changedAt;
}