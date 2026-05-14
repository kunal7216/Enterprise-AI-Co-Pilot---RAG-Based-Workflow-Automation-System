package com.enterprise.copilot.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ApprovalRequest {

    @NotNull(message = "Workflow ID is required")
    private Long workflowId;

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVE|REJECT",
            message = "Action must be exactly 'APPROVE' or 'REJECT'")
    private String action;

    private String comments;
}