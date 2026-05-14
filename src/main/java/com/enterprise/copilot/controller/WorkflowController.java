package com.enterprise.copilot.controller;

import com.enterprise.copilot.Dto.*;
import com.enterprise.copilot.enums.WorkflowStatus;
import com.enterprise.copilot.enums.WorkflowType;
import com.enterprise.copilot.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;   // ← FIXED (was: entity User)
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Workflows", description = "Workflow management APIs")
@SecurityRequirement(name = "bearerAuth")
public class WorkflowController {

    private final WorkflowService workflowService;

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document and create workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workflow created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ApiSuccessResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "workflowType", required = false, defaultValue = "GENERAL")
            WorkflowType workflowType,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Upload request from user: {}, file: {}", username, file.getOriginalFilename());

        WorkflowResponse response = workflowService.uploadAndProcessDocument(file, workflowType, username);  // ← FIXED

        log.info("Workflow created id={} status={}", response.getId(), response.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiSuccessResponse.of("Workflow created successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get workflows with pagination and optional filters")
    public ResponseEntity<ApiSuccessResponse> getWorkflows(
            @RequestParam(required = false) WorkflowStatus status,
            @RequestParam(required = false) WorkflowType workflowType,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Fetching workflows for user: {}", username);

        Page<WorkflowResponse> workflows = workflowService.getWorkflows(status, workflowType, pageable, username);  // ← FIXED

        return ResponseEntity.ok(ApiSuccessResponse.of("Workflows fetched successfully", workflows));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow found"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Workflow not found")
    })
    public ResponseEntity<ApiSuccessResponse> getWorkflowById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Fetching workflow id={} for user={}", id, username);

        WorkflowResponse workflow = workflowService.getWorkflowById(id, username);  // ← FIXED

        return ResponseEntity.ok(ApiSuccessResponse.of("Workflow fetched successfully", workflow));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RAG INSIGHTS  (separate endpoint — avoids recursion in toResponse)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/rag-insights")
    @Operation(summary = "Get RAG insights — similar historical workflows")
    public ResponseEntity<ApiSuccessResponse> getRagInsights(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        List<RagInsightDto> insights = workflowService.getRagInsights(id);  // ← FIXED (was RagInsight)

        return ResponseEntity.ok(ApiSuccessResponse.of("RAG insights fetched successfully", insights));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APPROVE / REJECT
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/approve")
    @Operation(summary = "Approve or reject a workflow")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Action completed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Workflow not found")
    })
    public ResponseEntity<ApiSuccessResponse> approveWorkflow(
            @Valid @RequestBody WorkflowApproval approvalDto,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Approval request id={} action={} user={}", approvalDto.getWorkflowId(), approvalDto.getAction(), username);

        workflowService.processApproval(approvalDto, username);  // ← FIXED

        return ResponseEntity.ok(
                ApiSuccessResponse.of("Workflow " + approvalDto.getAction() + " successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HISTORY
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/history")
    @Operation(summary = "Get audit trail for a workflow")
    public ResponseEntity<ApiSuccessResponse> getWorkflowHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        log.info("Fetching history for workflow id={}", id);
        List<WorkflowHistoryResponse> history = workflowService.getWorkflowHistory(id, currentUser != null ? currentUser.getUsername() : "anonymous");

        return ResponseEntity.ok(ApiSuccessResponse.of("Workflow history fetched successfully", history));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    @Operation(summary = "Get workflow statistics")
    public ResponseEntity<ApiSuccessResponse> getWorkflowStats(
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Fetching stats for user: {}", username);

        WorkflowStats stats = workflowService.getWorkflowStatistics(username);  // ← FIXED

        return ResponseEntity.ok(ApiSuccessResponse.of("Workflow statistics fetched successfully", stats));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/dashboard/insights")
    @Operation(summary = "Get dashboard insights — stats + chart data")
    public ResponseEntity<ApiSuccessResponse> getDashboardInsights(
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        DashboardInsightsResponse response = workflowService.getDashboardInsights(username);  // ← FIXED

        return ResponseEntity.ok(ApiSuccessResponse.of("Dashboard insights fetched successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BULK APPROVE
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/bulk-approve")
    @Operation(summary = "Bulk approve workflows")
    public ResponseEntity<ApiSuccessResponse> bulkApprove(
            @Valid @RequestBody BulkApprovalRequest bulkApprovalDto,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Bulk approval for {} workflows by user={}", bulkApprovalDto.getWorkflowIds().size(), username);

        int count = workflowService.bulkApprove(bulkApprovalDto, username);

        return ResponseEntity.ok(ApiSuccessResponse.of(count + " workflows processed successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETRY
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed workflow")
    public ResponseEntity<ApiSuccessResponse> retryWorkflow(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser) {   // ← FIXED

        String username = currentUser != null ? currentUser.getUsername() : "anonymous";
        log.info("Retry request for workflow id={} by user={}", id, username);

        WorkflowResponse workflow = workflowService.retryWorkflow(id, username);  // ← FIXED

        return ResponseEntity.ok(ApiSuccessResponse.of("Workflow retry initiated successfully", workflow));
    }
}
