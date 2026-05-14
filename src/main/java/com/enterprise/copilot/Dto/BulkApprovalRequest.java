package com.enterprise.copilot.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BulkApprovalRequest {

    @NotEmpty(message = "At least one workflow ID is required")
    private List<Long> workflowIds;

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVE|REJECT",
            message = "Action must be exactly 'APPROVE' or 'REJECT'")
    private String action;

    private String comments;
}