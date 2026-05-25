package com.enterprise.copilot.service;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Service.
 *
 * Retrieves historical invoice context from the database and formats it
 * for injection into the Ollama prompt. This gives the AI model knowledge
 * of past vendor behavior, approval patterns, and anomalies.
 *
 * Strategy:
 *  1. Fast vendor-name text search (SQL LIKE / JSONB)
 *  2. Semantic similarity search via pgvector embeddings
 *  3. Merge + deduplicate results
 *  4. Build enriched context string injected into the LLM prompt
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final WorkflowRepository workflowRepository;
    private final OllamaService ollamaService;
    private final RagConfigService ragConfigService;

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a rich context string from past similar invoices.
     * Used to augment the AI prompt with historical data.
     *
     * @param vendorName   extracted vendor name from current invoice
     * @param documentText full text of current invoice
     * @return formatted RagContext for prompt injection
     */
    @Cacheable(value = "ragContext", key = "#vendorName", unless = "#result.invoiceCount == 0")
    public RagContext buildContext(String vendorName, String documentText) {
        try {
            // 1. Get vendor history via text search (fast, no embedding needed)
            List<Workflow> vendorHistory = workflowRepository
                    .findByVendorInExtractedData(safe(vendorName));

            // 2. Get semantically similar invoices via pgvector
            List<Workflow> semanticMatches = getSimilarInvoicesSafe(documentText);

            // 3. Merge and deduplicate
            List<Workflow> combined = merge(vendorHistory, semanticMatches);

            if (combined.isEmpty()) {
                log.info("RAG: No history found for vendor '{}'", vendorName);
                return RagContext.empty();
            }

            // 4. Build stats
            double avgAmount = combined.stream()
                    .mapToDouble(this::extractAmount)
                    .filter(a -> a > 0)
                    .average()
                    .orElse(0.0);

            long approvedCount = combined.stream()
                    .filter(w -> w.getDecisionType() != null &&
                            w.getDecisionType().name().contains("APPROVED"))
                    .count();

            long escalatedCount = combined.stream()
                    .filter(w -> w.getDecisionType() != null &&
                            w.getDecisionType().name().contains("ESCALATED"))
                    .count();

            long rejectedCount = combined.stream()
                    .filter(w -> w.getDecisionType() != null &&
                            w.getDecisionType().name().contains("REJECTED"))
                    .count();

            double approvalRate = combined.size() > 0
                    ? (double) approvedCount / combined.size() * 100 : 0.0;

            // 5. Build formatted context string for LLM injection
            StringBuilder sb = new StringBuilder();
            sb.append("=== VENDOR HISTORY (").append(combined.size()).append(" past invoices) ===\n");
            sb.append("Vendor: ").append(safe(vendorName)).append("\n");
            sb.append(String.format("Average invoice amount: ₹%.2f%n", avgAmount));
            sb.append("Approval rate: ").append(String.format("%.0f%%", approvalRate)).append("\n");
            sb.append("Decisions — Approved: ").append(approvedCount)
                    .append(", Escalated: ").append(escalatedCount)
                    .append(", Rejected: ").append(rejectedCount).append("\n\n");

            sb.append("=== RECENT INVOICES ===\n");
            combined.stream().limit(5).forEach(w -> {
                double amt = extractAmount(w);
                String decision = w.getDecisionType() != null
                        ? w.getDecisionType().name() : "UNKNOWN";
                String date = w.getCreatedAt() != null
                        ? w.getCreatedAt().toLocalDate().toString() : "unknown date";

                sb.append(String.format("- %s: ₹%.2f → %s%n", date, amt, decision));

                if (w.getDecisionReason() != null && !w.getDecisionReason().isBlank()) {
                    sb.append("  Reason: ").append(truncate(w.getDecisionReason(), 100)).append("\n");
                }
            });

            // 6. Vendor risk signal
            String vendorRisk = approvalRate >= 80 ? "LOW"
                    : approvalRate >= 50 ? "MEDIUM" : "HIGH";
            sb.append("\nVendor Risk Profile: ").append(vendorRisk).append("\n");

            // BUG FIX: Use String.format instead of {:.0f} (Python syntax — invalid in SLF4J)
            log.info("RAG context built for vendor '{}': {} past invoices, {}% approval rate",
                    vendorName, combined.size(), String.format("%.0f", approvalRate));

            return new RagContext(
                    sb.toString(),
                    combined.size(),
                    avgAmount,
                    approvalRate,
                    vendorRisk
            );

        } catch (Exception e) {
            log.warn("RAG context building failed for vendor '{}': {}", vendorName, e.getMessage());
            return RagContext.empty();
        }
    }

    /**
     * Checks if a similar invoice already exists (duplicate detection via RAG).
     * Matches on amount (within 5% tolerance) AND invoice number if available.
     */
    public DuplicateCheckResult checkDuplicate(String vendorName, double amount) {
        return checkDuplicate(vendorName, amount, null);
    }

    /**
     * Overload with invoice number for stronger duplicate detection.
     */
    public DuplicateCheckResult checkDuplicate(String vendorName, double amount, String invoiceNumber) {
        try {
            List<Workflow> past = workflowRepository.findByVendorInExtractedData(safe(vendorName));
            for (Workflow w : past) {
                // Strong match: same invoice number
                if (invoiceNumber != null && !invoiceNumber.isBlank()
                        && w.getExtractedData() != null
                        && w.getExtractedData().contains(invoiceNumber)) {
                    return new DuplicateCheckResult(true, w.getId(),
                            String.format("Duplicate invoice number '%s' found in workflow #%d",
                                    invoiceNumber, w.getId()));
                }

                // Fuzzy match: amount within 5%
                double pastAmt = extractAmount(w);
                if (pastAmt > 0) {
                    double diff = Math.abs(amount - pastAmt) / pastAmt;
                    if (diff <= 0.05) {
                        return new DuplicateCheckResult(true, w.getId(),
                                String.format("Similar invoice #%d found (₹%.2f, %.1f%% match)",
                                        w.getId(), pastAmt, (1 - diff) * 100));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Duplicate RAG check failed: {}", e.getMessage());
        }
        return new DuplicateCheckResult(false, null, null);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Safe wrapper around semantic similarity search.
     * Returns empty list on any failure — never propagates exceptions.
     */
    private List<Workflow> getSimilarInvoicesSafe(String documentText) {
        try {
            List<Workflow> result = ollamaService.getSimilarWorkflowsWithThreshold(
                    documentText, 5, ragConfigService.getThreshold());
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("Semantic similarity search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Workflow> merge(List<Workflow> vendorHistory, List<Workflow> semantic) {
        return java.util.stream.Stream.concat(vendorHistory.stream(), semantic.stream())
                .filter(w -> w.getExtractedData() != null && !w.getExtractedData().isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                Workflow::getId,
                                w -> w,
                                (a, b) -> a,
                                java.util.LinkedHashMap::new
                        ),
                        map -> List.copyOf(map.values())
                ));
    }

    private double extractAmount(Workflow w) {
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

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    // ─────────────────────────────────────────────────────────────
    // RESULT RECORDS
    // ─────────────────────────────────────────────────────────────

    public record RagContext(
            String contextText,
            int invoiceCount,
            double avgAmount,
            double approvalRate,
            String vendorRisk
    ) {
        public boolean hasContext() {
            return invoiceCount > 0;
        }

        /**
         * Returns true if vendor has strong, trustworthy history.
         * Used by WorkflowService to lower confidence thresholds.
         */
        public boolean isTrustedVendor() {
            return invoiceCount >= 10 && approvalRate >= 90.0;
        }

        public static RagContext empty() {
            return new RagContext("", 0, 0.0, 0.0, "UNKNOWN");
        }
    }

    public record DuplicateCheckResult(
            boolean isDuplicate,
            Long matchedWorkflowId,
            String reason
    ) {}
}
