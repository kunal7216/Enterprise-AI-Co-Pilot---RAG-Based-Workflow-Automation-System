package com.enterprise.copilot.service;

import com.enterprise.copilot.Dto.*;
import com.enterprise.copilot.entity.User;
import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.entity.WorkflowHistory;
import com.enterprise.copilot.enums.DecisionType;
import com.enterprise.copilot.enums.Role;
import com.enterprise.copilot.enums.WorkflowStatus;
import com.enterprise.copilot.enums.WorkflowType;
import com.enterprise.copilot.exception.InvalidStateException;
import com.enterprise.copilot.exception.ResourceNotFoundException;
import com.enterprise.copilot.kafka.WorkflowKafkaProducer;
import com.enterprise.copilot.repository.UserRepository;
import com.enterprise.copilot.repository.WorkflowHistoryRepository;
import com.enterprise.copilot.repository.WorkflowRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OllamaService ollamaService;
    private final FraudDetectionService fraudDetectionService;
    private final RagService ragService;
    private final WorkflowKafkaProducer kafkaProducer;

    private static final double CONFIDENCE_THRESHOLD = 0.70;
    private static final double AUTO_APPROVE_CONFIDENCE = 0.85;
    private static final double AMOUNT_ESCALATE_LIMIT = 50_000.0;
    private static final double AMOUNT_AUTO_APPROVE = 10_000.0;
    private static final double ANOMALY_DEVIATION = 0.50;
    private static final double DLQ_CONFIDENCE_THRESHOLD = 0.4;

    // =========================================================================
    // PUBLIC CONTROLLER-FACING METHODS
    // =========================================================================

    public WorkflowResponse uploadAndProcessDocument(
            MultipartFile file, WorkflowType workflowType, String username) {
        return processDocument(file, username);
    }

    public Page<WorkflowResponse> getWorkflows(
            WorkflowStatus status, WorkflowType workflowType,
            Pageable pageable, String username) {
        String statusFilter = (status != null) ? status.name() : null;
        return listWorkflows(username, statusFilter, pageable);
    }

    public WorkflowResponse getWorkflowById(Long id, String username) {
        return getById(id);
    }

    public void processApproval(@Valid WorkflowApproval approvalDto, String username) {
        ApprovalRequest req = new ApprovalRequest();
        req.setWorkflowId(approvalDto.getWorkflowId());
        req.setAction(approvalDto.getAction());
        req.setComments(approvalDto.getComments());
        approveWorkflow(req, username);
    }

    public List<WorkflowHistoryResponse> getWorkflowHistory(Long id, String username) {
        return getHistory(id);
    }

    public WorkflowStats getWorkflowStatistics(String username) {
        DashboardStatsResponse stats = getStats(username);
        return WorkflowStats.builder()
                .totalWorkflows(stats.getTotalWorkflows())
                .pendingWorkflows(stats.getPendingWorkflows())
                .completedWorkflows(stats.getCompletedWorkflows())
                .rejectedWorkflows(stats.getRejectedWorkflows())
                .escalatedWorkflows(stats.getEscalatedWorkflows())
                .avgConfidenceScore(stats.getAvgConfidenceScore())
                .avgProcessingTimeSeconds(stats.getAvgProcessingTimeSeconds())
                .automationRate(stats.getAutomationRate())
                .build();
    }

    public DashboardInsightsResponse getDashboardInsights(String username) {
        WorkflowStats stats = getWorkflowStatistics(username);
        User user = getUser(username);
        boolean isAdmin = isManagerOrAdmin(user);

        List<ChartPointDto> statusChart = List.of(
                new ChartPointDto("PENDING", isAdmin
                        ? workflowRepository.countByStatus(WorkflowStatus.PENDING)
                        : workflowRepository.countByCreatedByAndStatus(user, WorkflowStatus.PENDING)),
                new ChartPointDto("PROCESSING", isAdmin
                        ? workflowRepository.countByStatus(WorkflowStatus.PROCESSING)
                        : workflowRepository.countByCreatedByAndStatus(user, WorkflowStatus.PROCESSING)),
                new ChartPointDto("COMPLETED", isAdmin
                        ? workflowRepository.countByStatus(WorkflowStatus.COMPLETED)
                        : workflowRepository.countByCreatedByAndStatus(user, WorkflowStatus.COMPLETED)),
                new ChartPointDto("ESCALATED", isAdmin
                        ? workflowRepository.countByStatus(WorkflowStatus.ESCALATED)
                        : workflowRepository.countByCreatedByAndStatus(user, WorkflowStatus.ESCALATED)),
                new ChartPointDto("REJECTED", isAdmin
                        ? workflowRepository.countByStatus(WorkflowStatus.REJECTED)
                        : workflowRepository.countByCreatedByAndStatus(user, WorkflowStatus.REJECTED)));

        List<ChartPointDto> decisionChart = isAdmin
                ? List.of(
                        new ChartPointDto("AUTO_APPROVED",
                                workflowRepository.countByDecisionType(DecisionType.AUTO_APPROVED)),
                        new ChartPointDto("ESCALATED_TO_MANAGER",
                                workflowRepository.countByDecisionType(DecisionType.ESCALATED_TO_MANAGER)),
                        new ChartPointDto("MANAGER_APPROVED",
                                workflowRepository.countByDecisionType(DecisionType.MANAGER_APPROVED)),
                        new ChartPointDto("MANAGER_REJECTED",
                                workflowRepository.countByDecisionType(DecisionType.MANAGER_REJECTED)))
                : List.of(
                        new ChartPointDto("AUTO_APPROVED", 0L),
                        new ChartPointDto("ESCALATED_TO_MANAGER", 0L),
                        new ChartPointDto("MANAGER_APPROVED", 0L),
                        new ChartPointDto("MANAGER_REJECTED", 0L));

        return DashboardInsightsResponse.builder()
                .stats(stats)
                .statusChart(statusChart)
                .decisionChart(decisionChart)
                .build();
    }

    public List<RagInsightDto> getRagInsights(Long workflowId) {
        Workflow current = getWorkflow(workflowId);

        if (current.getExtractedData() == null || current.getExtractedData().isBlank()) {
            return List.of();
        }

        List<Workflow> similar = ollamaService.getSimilarWorkflows(current.getExtractedData(), 3);

        return similar.stream()
                .filter(w -> !w.getId().equals(current.getId()))
                .map(w -> RagInsightDto.builder()
                        .workflowId(w.getId())
                        .documentName(w.getDocumentName())
                        .matchedVendor(extractVendorFromJson(w.getExtractedData()))
                        .matchedAmount(parseAmountFromJson(w.getExtractedData()))
                        .matchedDecision(w.getDecisionType() != null ? w.getDecisionType().name() : null)
                        .confidenceScore(w.getConfidenceScore())
                        .summary(buildRagSummary(w))
                        .build())
                .collect(Collectors.toList());
    }

    public WorkflowResponse retryWorkflow(Long id, String username) {
        User user = getUser(username);
        Workflow wf = getWorkflow(id);

        if (wf.getStatus() != WorkflowStatus.FAILED
                && wf.getStatus() != WorkflowStatus.ESCALATED) {
            throw new InvalidStateException(
                    "Only FAILED or ESCALATED workflows can be retried. Current: " + wf.getStatus());
        }

        WorkflowStatus previous = wf.getStatus();
        wf.setStatus(WorkflowStatus.PROCESSING);
        wf.setDecisionType(null);
        wf.setReviewedBy(null);
        wf.setReviewedAt(null);
        wf.setReviewComments(null);
        wf.setDecisionReason(null);
        workflowRepository.save(wf);

        addHistory(wf, previous, WorkflowStatus.PROCESSING, user, "Retry initiated by " + username);

        log.info("Workflow #{} retry initiated by {}", id, username);
        return toResponse(wf);
    }

    /**
     * Returns all workflows that ended up in the DLQ (status = FAILED after
     * exhausting retries).
     */
    public List<WorkflowResponse> getDlqWorkflows() {
        return workflowRepository.findByStatus(WorkflowStatus.FAILED,
                org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // INTERNAL PROCESSING — RAG-enhanced pipeline with Kafka DLQ
    // =========================================================================

    public WorkflowResponse processDocument(MultipartFile file, String username) {
        User user = getUser(username);
        long startMs = System.currentTimeMillis();

        // 1. Save initial workflow record
        Workflow wf = workflowRepository.save(Workflow.builder()
                .documentName(file.getOriginalFilename())
                .documentType(detectType(file))
                .status(WorkflowStatus.PROCESSING)
                .createdBy(user)
                .build());

        addHistory(wf, WorkflowStatus.PENDING, WorkflowStatus.PROCESSING,
                user, "Document received and processing started");

        try {
            // 2. Extract raw text from file
            String text = extractText(file);

            // 3. Quick rule-based vendor extraction for RAG lookup
            String prelimVendor = extractVendorQuick(text);

            // 4. RAG: Build historical context from past invoices (dual-strategy)
            RagService.RagContext ragContext = ragService.buildContext(prelimVendor, text);
            log.info("RAG context for vendor '{}': {} past invoices found, vendor risk: {}",
                    prelimVendor, ragContext.invoiceCount(), ragContext.vendorRisk());

            // 5. AI Extraction with RAG context injected into prompt
            OllamaService.ExtractionResult extracted = ollamaService
                    .extractFieldsWithContext(text, ragContext.contextText());

            double confidence = extracted.finalConfidence();

            // 6. RAG confidence adjustments based on vendor history
            if (ragContext.hasContext()) {
                if (ragContext.approvalRate() >= 80.0) {
                    confidence = Math.min(1.0, confidence + 0.05);
                    log.info("RAG confidence boost (+0.05) for vendor '{}': {}% approval rate",
                            extracted.vendorName(), String.format("%.0f", ragContext.approvalRate()));
                }
                if ("HIGH".equalsIgnoreCase(ragContext.vendorRisk())) {
                    confidence = Math.max(0.0, confidence - 0.10);
                    log.info("RAG confidence penalty (-0.10) for high-risk vendor '{}'",
                            extracted.vendorName());
                }
            }

            // 7. Fraud detection
            FraudDetectionService.FraudResult fraudResult = fraudDetectionService.analyze(extracted,
                    wf.getDocumentName(), LocalDateTime.now());

            // 8. Route to Kafka DLQ if confidence is critically low
            if (confidence < DLQ_CONFIDENCE_THRESHOLD) {
                log.warn("Workflow #{} confidence {:.2f} below DLQ threshold {} — publishing to Kafka",
                        wf.getId(), confidence, DLQ_CONFIDENCE_THRESHOLD);
                wf.setConfidenceScore(confidence);
                wf.setStatus(WorkflowStatus.ESCALATED);
                wf.setDecisionType(DecisionType.ESCALATED_TO_MANAGER);
                wf.setDecisionReason(String.format(
                        "Confidence %.2f below minimum threshold — queued for retry", confidence));
                wf.setProcessingTimeMs(System.currentTimeMillis() - startMs);
                workflowRepository.save(wf);
                kafkaProducer.publishFailed(wf.getId());
                return toResponse(wf);
            }

            // 9. Agent decision with RAG context
            AgentDecision decision = runAgent(extracted, confidence, fraudResult, ragContext);

            long elapsed = System.currentTimeMillis() - startMs;

            // 10. AI Explanation with RAG context
            String aiExplanation = ollamaService.explainDecision(
                    extracted, decision.decisionType().name(),
                    fraudResult.reasons(), ragContext.contextText());

            // 11. Save final workflow state
            wf.setExtractedData(extracted.jsonData());
            wf.setConfidenceScore(confidence);
            wf.setAiRecommendation(aiExplanation);
            wf.setStatus(decision.status());
            wf.setDecisionType(decision.decisionType());
            wf.setDecisionReason(decision.reason());
            wf.setProcessingTimeMs(elapsed);
            wf.setReviewComments(buildReviewNotes(decision.reason(), fraudResult));
            workflowRepository.save(wf);

            // 12. Store embedding for future RAG lookups
            ollamaService.updateWorkflowEmbedding(wf.getId(), text);

            addHistory(wf, WorkflowStatus.PROCESSING, decision.status(), user, decision.reason());

            log.info("Workflow #{} processed in {}ms | confidence={} | rag={} past invoices | " +
                    "fraud={}/{} | decision={}",
                    wf.getId(), elapsed,
                    String.format("%.2f", confidence),
                    ragContext.invoiceCount(),
                    fraudResult.score(), fraudResult.riskLevel(),
                    decision.decisionType());

            return toResponse(wf);

        } catch (Exception e) {
            log.warn("AI processing failed, using fallback: {}", e.getMessage());
            long elapsed = System.currentTimeMillis() - startMs;

            wf.setExtractedData("{\"source\":\"fallback\",\"reason\":\"" + safeJson(e.getMessage()) + "\"}");
            wf.setConfidenceScore(0.55);
            wf.setAiRecommendation("AI explanation unavailable");
            wf.setStatus(WorkflowStatus.ESCALATED);
            wf.setDecisionType(DecisionType.ESCALATED_TO_MANAGER);
            wf.setDecisionReason("AI unavailable — workflow escalated for manual review.");
            wf.setProcessingTimeMs(elapsed);
            wf.setReviewComments("AI unavailable — escalated for manual review");
            workflowRepository.save(wf);

            // Publish to Kafka for retry on AI failure
            kafkaProducer.publishFailed(wf.getId());
            log.info("Workflow #{} published to Kafka for retry after AI failure", wf.getId());

            addHistory(wf, WorkflowStatus.PROCESSING, WorkflowStatus.ESCALATED,
                    user, "AI unavailable — escalated for manual review");

            return toResponse(wf);
        }
    }

    // =========================================================================
    // AGENT DECISION ENGINE — RAG-aware
    // =========================================================================

    private AgentDecision runAgent(
            OllamaService.ExtractionResult extracted,
            double confidence,
            FraudDetectionService.FraudResult fraudResult,
            RagService.RagContext ragContext) {

        double effectiveThreshold = CONFIDENCE_THRESHOLD;
        if (ragContext.isTrustedVendor()) {
            effectiveThreshold = 0.65;
            log.info("RAG: lowering confidence threshold to {} for trusted vendor '{}'",
                    effectiveThreshold, extracted.vendorName());
        }

        if (confidence < effectiveThreshold) {
            return new AgentDecision(WorkflowStatus.ESCALATED,
                    DecisionType.ESCALATED_TO_MANAGER,
                    String.format("Confidence %.2f below threshold %.2f — needs human review",
                            confidence, effectiveThreshold));
        }

        if ("HIGH".equalsIgnoreCase(fraudResult.riskLevel())) {
            return new AgentDecision(WorkflowStatus.ESCALATED,
                    DecisionType.ESCALATED_TO_MANAGER,
                    "High fraud risk detected: " + fraudResult.reasons());
        }

        if (extracted.amount() > AMOUNT_ESCALATE_LIMIT) {
            String ragNote = ragContext.hasContext() && ragContext.avgAmount() > 0
                    ? String.format(" (historical avg: ₹%.2f)", ragContext.avgAmount())
                    : "";
            return new AgentDecision(WorkflowStatus.ESCALATED,
                    DecisionType.ESCALATED_TO_MANAGER,
                    String.format("Amount ₹%.2f exceeds ₹%.2f manager approval threshold%s",
                            extracted.amount(), AMOUNT_ESCALATE_LIMIT, ragNote));
        }

        if (!extracted.isVendorApproved()) {
            return new AgentDecision(WorkflowStatus.ESCALATED,
                    DecisionType.ESCALATED_TO_MANAGER,
                    "Vendor '" + extracted.vendorName() + "' is not in the approved vendor list");
        }

        if (ragContext.hasContext() && ragContext.avgAmount() > 0) {
            double deviation = Math.abs(extracted.amount() - ragContext.avgAmount())
                    / ragContext.avgAmount();
            if (deviation > ANOMALY_DEVIATION) {
                return new AgentDecision(WorkflowStatus.ESCALATED,
                        DecisionType.ESCALATED_TO_MANAGER,
                        String.format("RAG anomaly: ₹%.2f deviates %.0f%% from historical avg ₹%.2f",
                                extracted.amount(), deviation * 100, ragContext.avgAmount()));
            }
        } else {
            List<Workflow> pastWfs = workflowRepository
                    .findByVendorInExtractedData(extracted.vendorName());
            if (pastWfs.size() >= 3) {
                double avgAmt = pastWfs.stream()
                        .mapToDouble(w -> parseAmountFromJson(w.getExtractedData()))
                        .filter(a -> a > 0)
                        .average()
                        .orElse(0.0);
                if (avgAmt > 0) {
                    double deviation = Math.abs(extracted.amount() - avgAmt) / avgAmt;
                    if (deviation > ANOMALY_DEVIATION) {
                        return new AgentDecision(WorkflowStatus.ESCALATED,
                                DecisionType.ESCALATED_TO_MANAGER,
                                String.format("Anomaly: %.0f%% deviation from historical avg ₹%.2f",
                                        deviation * 100, avgAmt));
                    }
                }
            }
        }

        String aiRec = ollamaService.getApprovalRecommendation(extracted, ragContext.contextText());

        if (extracted.amount() < AMOUNT_AUTO_APPROVE
                && confidence > AUTO_APPROVE_CONFIDENCE
                && "LOW".equalsIgnoreCase(fraudResult.riskLevel())
                && !fraudResult.duplicate()
                && "APPROVE".equalsIgnoreCase(aiRec)) {

            String ragNote = ragContext.hasContext()
                    ? String.format(", RAG: %d past invoices (%.0f%% approved)",
                            ragContext.invoiceCount(), ragContext.approvalRate())
                    : "";
            return new AgentDecision(WorkflowStatus.COMPLETED,
                    DecisionType.AUTO_APPROVED,
                    String.format("Auto-approved: ₹%.2f < ₹%.2f, confidence %.2f > %.2f, fraud LOW, AI=APPROVE%s",
                            extracted.amount(), AMOUNT_AUTO_APPROVE,
                            confidence, AUTO_APPROVE_CONFIDENCE, ragNote));
        }

        return new AgentDecision(WorkflowStatus.ESCALATED,
                DecisionType.ESCALATED_TO_MANAGER,
                "Escalated for human review per enterprise policy");
    }

    // =========================================================================
    // APPROVE / BULK APPROVE
    // =========================================================================

    public WorkflowResponse approveWorkflow(ApprovalRequest req, String managerUsername) {
        User manager = getUser(managerUsername);
        Workflow wf = getWorkflow(req.getWorkflowId());

        if (wf.getStatus() != WorkflowStatus.ESCALATED) {
            throw new InvalidStateException(
                    "Only ESCALATED workflows can be reviewed. Current: " + wf.getStatus());
        }

        WorkflowStatus prev = wf.getStatus();
        boolean approve = "APPROVE".equalsIgnoreCase(req.getAction());
        WorkflowStatus next = approve ? WorkflowStatus.COMPLETED : WorkflowStatus.REJECTED;
        DecisionType dt = approve ? DecisionType.MANAGER_APPROVED : DecisionType.MANAGER_REJECTED;

        wf.setStatus(next);
        wf.setDecisionType(dt);
        wf.setReviewedBy(manager);
        wf.setReviewedAt(LocalDateTime.now());
        wf.setReviewComments(req.getComments());
        wf.setDecisionReason(req.getAction() + " by " + managerUsername
                + (req.getComments() != null ? ": " + req.getComments() : ""));
        workflowRepository.save(wf);

        addHistory(wf, prev, next, manager,
                req.getAction() + " by " + managerUsername
                        + (req.getComments() != null ? ": " + req.getComments() : ""));

        return toResponse(wf);
    }

    public int bulkApprove(BulkApprovalRequest req, String managerUsername) {
        return (int) req.getWorkflowIds().stream()
                .filter(id -> {
                    try {
                        ApprovalRequest single = new ApprovalRequest();
                        single.setWorkflowId(id);
                        single.setAction(req.getAction());
                        single.setComments(req.getComments());
                        approveWorkflow(single, managerUsername);
                        return true;
                    } catch (Exception e) {
                        log.warn("Failed to process workflow id={}", id, e);
                        return false;
                    }
                })
                .count();
    }

    // =========================================================================
    // LIST / GET HELPERS
    // =========================================================================

    public Page<WorkflowResponse> listWorkflows(String username, String statusFilter, Pageable pageable) {
        User user = getUser(username);
        boolean isAdmin = isManagerOrAdmin(user);

        Page<Workflow> page;
        if (statusFilter != null && !statusFilter.isBlank()) {
            WorkflowStatus status = WorkflowStatus.valueOf(statusFilter.toUpperCase());
            page = isAdmin
                    ? workflowRepository.findByStatus(status, pageable)
                    : workflowRepository.findByCreatedByAndStatus(user, status, pageable);
        } else {
            page = isAdmin
                    ? workflowRepository.findAll(pageable)
                    : workflowRepository.findByCreatedBy(user, pageable);
        }

        return page.map(this::toResponse);
    }

    public WorkflowResponse getById(Long id) {
        return toResponse(getWorkflow(id));
    }

    public List<WorkflowHistoryResponse> getHistory(Long workflowId) {
        Workflow wf = getWorkflow(workflowId);
        return historyRepository.findByWorkflowOrderByChangedAtDesc(wf).stream()
                .map(h -> WorkflowHistoryResponse.builder()
                        .id(h.getId())
                        .fromStatus(h.getFromStatus() != null ? h.getFromStatus().name() : null)
                        .toStatus(h.getToStatus().name())
                        .changedByUsername(h.getChangedBy() != null
                                ? h.getChangedBy().getUsername()
                                : "system")
                        .notes(h.getNotes())
                        .changedAt(h.getChangedAt() != null ? h.getChangedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());
    }

    public DashboardStatsResponse getStats(String username) {
        User user = getUser(username);
        boolean isAdmin = isManagerOrAdmin(user);

        long total = isAdmin ? workflowRepository.count() : workflowRepository.countByCreatedBy(user);
        long pending = countStat(user, WorkflowStatus.PENDING, isAdmin);
        long completed = countStat(user, WorkflowStatus.COMPLETED, isAdmin);
        long rejected = countStat(user, WorkflowStatus.REJECTED, isAdmin);
        long escalated = countStat(user, WorkflowStatus.ESCALATED, isAdmin);

        double avgConf = workflowRepository.findAverageConfidenceScore().orElse(0.0);
        double avgTime = workflowRepository.findAverageProcessingTimeMs().orElse(0.0) / 1000.0;

        long autoApproved = workflowRepository.countByDecisionType(DecisionType.AUTO_APPROVED);
        double autoRate = total > 0 ? (double) autoApproved / total * 100 : 0.0;

        return DashboardStatsResponse.builder()
                .totalWorkflows(total)
                .pendingWorkflows(pending)
                .completedWorkflows(completed)
                .rejectedWorkflows(rejected)
                .escalatedWorkflows(escalated)
                .avgConfidenceScore(round(avgConf, 3))
                .avgProcessingTimeSeconds(round(avgTime, 2))
                .automationRate(round(autoRate, 1))
                .build();
    }

    // =========================================================================
    // toResponse
    // =========================================================================

    private WorkflowResponse toResponse(Workflow w) {
        return WorkflowResponse.builder()
                .id(w.getId())
                .status(w.getStatus().name())
                .decisionType(w.getDecisionType() != null ? w.getDecisionType().name() : null)
                .documentName(w.getDocumentName())
                .documentType(w.getDocumentType())
                .extractedData(w.getExtractedData())
                .confidenceScore(w.getConfidenceScore())
                .aiRecommendation(w.getAiRecommendation())
                .processingTimeMs(w.getProcessingTimeMs())
                .decisionReason(w.getDecisionReason())
                .reviewComments(w.getReviewComments())
                .reviewedAt(w.getReviewedAt() != null ? w.getReviewedAt().toString() : null)
                .reviewedByUsername(w.getReviewedBy() != null ? w.getReviewedBy().getUsername() : null)
                .createdByUsername(w.getCreatedBy() != null ? w.getCreatedBy().getUsername() : null)
                .createdAt(w.getCreatedAt() != null ? w.getCreatedAt().toString() : null)
                .updatedAt(w.getUpdatedAt() != null ? w.getUpdatedAt().toString() : null)
                .ragInsights(null)
                .build();
    }

    // =========================================================================
    // PRIVATE UTILITIES
    // =========================================================================

    private String extractText(MultipartFile file) throws IOException {
        return switch (detectType(file)) {
            case "PDF" -> extractPdf(file);
            case "DOCX" -> extractDocx(file);
            case "XLSX" -> extractXlsx(file);
            case "TXT" -> new String(file.getBytes());
            default -> throw new UnsupportedOperationException(
                    "Unsupported file type. Upload PDF, DOCX, XLSX, or TXT.");
        };
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return sb.toString();
        }
    }

    private String extractXlsx(MultipartFile file) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            wb.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> sb.append(cell.toString()).append("\t"))));
            return sb.toString();
        }
    }

    private String detectType(MultipartFile file) {
        String name = Objects.requireNonNull(file.getOriginalFilename(), "No filename").toLowerCase();
        if (name.endsWith(".pdf"))
            return "PDF";
        if (name.endsWith(".docx"))
            return "DOCX";
        if (name.endsWith(".xlsx"))
            return "XLSX";
        if (name.endsWith(".txt"))
            return "TXT";
        return "UNKNOWN";
    }

    private String extractVendorQuick(String text) {
        String lower = text.toLowerCase();
        String[] knownVendors = {
                "infosys", "wipro", "tcs", "accenture", "cognizant",
                "amazon", "microsoft", "google", "ibm", "oracle",
                "deloitte", "pwc", "kpmg", "ey", "capgemini"
        };
        for (String v : knownVendors) {
            if (lower.contains(v))
                return v;
        }
        return "";
    }

    private double parseAmountFromJson(String jsonData) {
        if (jsonData == null)
            return 0.0;
        try {
            if (jsonData.contains("\"amount\":")) {
                String sub = jsonData.split("\"amount\":")[1].split("[,}]")[0].trim();
                return Double.parseDouble(sub);
            }
        } catch (Exception ignored) {
        }
        return 0.0;
    }

    private String extractVendorFromJson(String jsonData) {
        if (jsonData == null)
            return "";
        try {
            String key = "\"vendor_name\":";
            if (jsonData.contains(key)) {
                return jsonData.split(key)[1].split("[,}]")[0].trim().replace("\"", "");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String buildRagSummary(Workflow w) {
        return "Matched historical workflow '" + w.getDocumentName()
                + "' with decision "
                + (w.getDecisionType() != null ? w.getDecisionType().name() : "UNKNOWN")
                + " and confidence "
                + (w.getConfidenceScore() != null ? w.getConfidenceScore() : 0.0);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Workflow getWorkflow(Long id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + id));
    }

    private void addHistory(Workflow wf, WorkflowStatus from,
            WorkflowStatus to, User by, String notes) {
        historyRepository.save(WorkflowHistory.builder()
                .workflow(wf)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(by)
                .notes(notes)
                .build());
    }

    private long countStat(User user, WorkflowStatus status, boolean isAdmin) {
        return isAdmin
                ? workflowRepository.countByStatus(status)
                : workflowRepository.countByCreatedByAndStatus(user, status);
    }

    private boolean isManagerOrAdmin(User user) {
        return user.getRole() == Role.MANAGER || user.getRole() == Role.ADMIN;
    }

    private double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    private String buildReviewNotes(String decisionReason, FraudDetectionService.FraudResult fraudResult) {
        return decisionReason
                + " | FraudScore=" + fraudResult.score()
                + ", Risk=" + fraudResult.riskLevel()
                + ", Duplicate=" + fraudResult.duplicate()
                + ", Reasons=" + fraudResult.reasons();
    }

    private String safeJson(String value) {
        if (value == null)
            return "";
        return value.replace("\"", "'").replace("\n", " ");
    }

    private record AgentDecision(
            WorkflowStatus status,
            DecisionType decisionType,
            String reason) {
    }
}