package com.enterprise.copilot.service;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final WorkflowRepository workflowRepository;

    // ── Weights ──────────────────────────────────────────────
    private static final int W_ROUND_NUMBER = 15;
    private static final int W_WEEKEND      = 10;
    private static final int W_VENDOR_UNKNOWN = 20;
    private static final int W_AMOUNT_SPIKE = 25;
    private static final int W_DUPLICATE    = 30;
    private static final int W_MISSING_FIELDS = 20;

    public FraudResult analyze(OllamaService.ExtractionResult extracted,
                               String documentName,
                               LocalDateTime uploadTime) {

        int score = 0;
        StringBuilder reasons = new StringBuilder();

        double amount = safe(extracted.amount());
        String vendor = safeStr(extracted.vendorName());

        // ── 1. Round number check ─────────────────────────────
        if (amount > 0 && amount % 1000 == 0) {
            score += W_ROUND_NUMBER;
            reasons.append("Round amount detected; ");
        }

        // ── 2. Weekend check ───────────────────────────────────
        if (uploadTime != null) {
            DayOfWeek day = uploadTime.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                score += W_WEEKEND;
                reasons.append("Weekend submission; ");
            }
        }

        // ── 3. Vendor check ────────────────────────────────────
        if (!extracted.isVendorApproved()) {
            score += W_VENDOR_UNKNOWN;
            reasons.append("Unknown vendor: ").append(vendor).append("; ");
        }

        // ── 4. Amount spike — FIXED: use text search instead of broken vector placeholder ──
        if (!vendor.isBlank()) {
            List<Workflow> past = workflowRepository.findByVendorInExtractedData(vendor);

            if (past != null && past.size() >= 2) {
                double avg = past.stream()
                        .mapToDouble(this::extractAmountFromEntity)
                        .filter(a -> a > 0)
                        .average()
                        .orElse(0.0);

                if (avg > 0 && amount > avg * 2.0) {
                    score += W_AMOUNT_SPIKE;
                    reasons.append("Amount spike detected; ");
                }
            }
        }

        // ── 5. Duplicate detection — FIXED: use text search instead of broken vector placeholder ──
        DuplicateResult dup = checkDuplicate(vendor, amount);

        if (dup.duplicate()) {
            score += W_DUPLICATE;
            reasons.append("Duplicate invoice found ID=").append(dup.workflowId()).append("; ");
        }

        // ── 6. Missing fields ──────────────────────────────────
        int missing = 0;
        if (extracted.invoiceNumber() == null) missing++;
        if (extracted.invoiceDate() == null)   missing++;
        if (vendor.isBlank() || vendor.equalsIgnoreCase("unknown")) missing++;

        if (missing >= 2) {
            score += W_MISSING_FIELDS;
            reasons.append("Missing critical fields; ");
        }

        // ── Final score ────────────────────────────────────────
        score = Math.min(score, 100);

        String risk =
                score >= 70 ? "HIGH" :
                        score >= 40 ? "MEDIUM" : "LOW";

        return new FraudResult(
                score,
                risk,
                reasons.length() == 0 ? "Clean invoice" : reasons.toString(),
                dup.duplicate()
        );
    }

    // ── Duplicate check — uses text search, no vector needed ─
    private DuplicateResult checkDuplicate(String vendor, double amount) {
        if (vendor.isBlank()) return new DuplicateResult(false, null);

        List<Workflow> list = workflowRepository.findByVendorInExtractedData(vendor);

        for (Workflow w : list) {
            double past = extractAmountFromEntity(w);
            if (past > 0) {
                double diff = Math.abs(amount - past) / past;
                if (diff <= 0.05) {
                    return new DuplicateResult(true, w.getId());
                }
            }
        }

        return new DuplicateResult(false, null);
    }

    // ── Safe parsing helpers ─────────────────────────────────
    private double safe(double val) {
        return Double.isNaN(val) ? 0.0 : val;
    }

    private String safeStr(String val) {
        return val == null ? "" : val.trim();
    }

    private double extractAmountFromEntity(Workflow w) {
        try {
            String data = w.getExtractedData();
            if (data == null) return 0.0;
            String key = "\"amount\":";
            if (data.contains(key)) {
                String part = data.split(key)[1].split("[,}]")[0];
                return Double.parseDouble(part.trim());
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    // ── DTOs ────────────────────────────────────────────────
    public record FraudResult(
            int score,
            String riskLevel,
            String reasons,
            boolean duplicate
    ) {}

    public record DuplicateResult(
            boolean duplicate,
            Long workflowId
    ) {}
}
