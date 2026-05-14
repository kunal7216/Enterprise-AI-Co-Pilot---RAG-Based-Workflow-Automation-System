package com.enterprise.copilot.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowApproval {

    @NotNull(message = "Workflow ID is required")
    private Long workflowId;

    @NotBlank(message = "Action is required")
    private String action;      // APPROVE or REJECT

    private String comments;    // optional
}