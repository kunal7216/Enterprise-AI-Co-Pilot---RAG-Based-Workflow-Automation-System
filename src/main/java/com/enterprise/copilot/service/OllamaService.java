package com.enterprise.copilot.service;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all communication with Ollama.
 * Supports:
 * - invoice field extraction with RAG context
 * - approval recommendation with vendor history
 * - embeddings generation (nomic-embed-text)
 * - pgvector-based semantic search
 * - explainable AI summaries
 * Falls back to rule-based extraction if Ollama is unavailable.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OllamaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WorkflowRepository workflowRepository;

    @Value("${ollama.base-url:http://enterprise-ollama:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3}")
    private String model;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    private static final Set<String> APPROVED_VENDORS = Set.of(
            "infosys", "wipro", "tcs", "accenture", "cognizant",
            "amazon", "microsoft", "google", "ibm", "oracle",
            "deloitte", "pwc", "kpmg", "ey", "capgemini"
    );

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    /**
     * Extract invoice fields using Ollama (no RAG context).
     */
    public ExtractionResult extractFields(String documentText) {
        return extractFieldsWithContext(documentText, "");
    }

    /**
     * Extract invoice fields with RAG context injected into the prompt.
     * This is the main RAG-enhanced extraction method.
     */
    public ExtractionResult extractFieldsWithContext(String documentText, String ragContext) {
        try {
            String prompt = buildExtractionPrompt(documentText, ragContext);
            String rawResponse = callOllama(prompt);
            log.info("Ollama raw response (first 300 chars): {}",
                    rawResponse.length() > 300 ? rawResponse.substring(0, 300) : rawResponse);
            return parseExtractionResponse(rawResponse);
        } catch (Exception e) {
            log.warn("Ollama extraction failed ({}), using rule-based fallback", e.getMessage());
            return fallbackExtraction(documentText);
        }
    }

    /**
     * Ask Ollama for final workflow recommendation using extracted fields + RAG context.
     */
    public String getApprovalRecommendation(ExtractionResult e, String ragContext) {
        String historySection = (ragContext != null && !ragContext.isBlank())
                ? "\nVendor History:\n" + ragContext + "\n" : "";

        String prompt = "Enterprise invoice policy review:" +
                "\nVendor: " + safe(e.vendorName()) +
                "\nAmount: ₹" + String.format("%.2f", e.amount()) +
                "\nVendor approved: " + e.isVendorApproved() +
                "\nConfidence: " + String.format("%.2f", e.finalConfidence()) +
                historySection +
                "\nReply with ONE word only: APPROVE, ESCALATE, or REJECT";

        try {
            String resp = callOllama(prompt).trim().toUpperCase();
            if (resp.contains("APPROVE")) return "APPROVE";
            if (resp.contains("REJECT")) return "REJECT";
            return "ESCALATE";
        } catch (Exception ex) {
            log.warn("Ollama recommendation failed: {}", ex.getMessage());
            return "ESCALATE";
        }
    }

    /**
     * Backwards-compatible overload without RAG context.
     */
    public String getApprovalRecommendation(ExtractionResult e) {
        return getApprovalRecommendation(e, "");
    }

    /**
     * Explain final decision for UI / audit trail.
     */
    public String explainDecision(ExtractionResult e, String finalDecision,
                                  String fraudReasons, String ragContext) {
        String historySection = (ragContext != null && !ragContext.isBlank())
                ? "\nVendor History Context:\n" + ragContext + "\n" : "";

        String prompt = "Explain this invoice decision in 3 short bullet points.\n" +
                "Vendor: " + safe(e.vendorName()) + "\n" +
                "Amount: ₹" + String.format("%.2f", e.amount()) + "\n" +
                "Vendor Approved: " + e.isVendorApproved() + "\n" +
                "Confidence: " + String.format("%.2f", e.finalConfidence()) + "\n" +
                "Fraud Signals: " + safe(fraudReasons) + "\n" +
                "Final Decision: " + safe(finalDecision) +
                historySection +
                "\nKeep it concise, professional, and business-friendly.";

        try {
            return callOllama(prompt).trim();
        } catch (Exception ex) {
            log.warn("Failed to generate decision explanation: {}", ex.getMessage());
            return "Decision explanation unavailable at this time.";
        }
    }

    /**
     * Backwards-compatible overload without RAG context.
     */
    public String explainDecision(ExtractionResult e, String finalDecision, String fraudReasons) {
        return explainDecision(e, finalDecision, fraudReasons, "");
    }

    /**
     * Generate embedding vector for semantic search.
     */
    public List<Double> generateEmbedding(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "prompt", truncate(text, 4000)
        );

        ResponseEntity<JsonNode> res = restTemplate.postForEntity(
                ollamaBaseUrl + "/api/embeddings",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RuntimeException("Embedding API failed: " + res.getStatusCode());
        }

        JsonNode embNode = res.getBody().path("embedding");
        if (!embNode.isArray() || embNode.isEmpty()) {
            throw new RuntimeException("Invalid embedding response from Ollama");
        }

        return objectMapper.convertValue(
                embNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
        );
    }

    /**
     * Store embedding in workflow row after processing (for future RAG lookups).
     */
    public void updateWorkflowEmbedding(Long workflowId, String documentText) {
        try {
            List<Double> embedding = generateEmbedding(documentText);
            String vector = toPgVector(embedding);
            workflowRepository.updateEmbedding(workflowId, vector);
            log.debug("Embedding stored for workflow #{}", workflowId);
        } catch (Exception e) {
            log.warn("Failed to update embedding for workflow {}: {}", workflowId, e.getMessage());
        }
    }

    /**
     * Returns semantically similar workflows for RAG context.
     * BUG FIX: Wrapped in try-catch, null-safe return.
     */
    public List<Workflow> getSimilarWorkflows(String documentText, int limit) {
        try {
            List<Double> embedding = generateEmbedding(documentText);
            String vector = toPgVector(embedding);
            List<Workflow> result = workflowRepository.findSimilarWithLimit(vector, limit);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.warn("getSimilarWorkflows failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get semantic context string from similar workflows (legacy method).
     */
    public String getRagContext(String documentText) {
        try {
            List<Double> embedding = generateEmbedding(documentText);
            String vector = toPgVector(embedding);
            List<Workflow> similar = workflowRepository.findSimilar(vector);

            if (similar == null || similar.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            int index = 1;
            for (Workflow w : similar) {
                if (w.getExtractedData() == null || w.getExtractedData().isBlank()) continue;
                sb.append("Past invoice ").append(index++).append(":\n")
                        .append(w.getExtractedData()).append("\n");
                if (w.getAiRecommendation() != null && !w.getAiRecommendation().isBlank())
                    sb.append("Past decision: ").append(w.getAiRecommendation()).append("\n");
                sb.append("---\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("RAG context retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PROMPT BUILDER
    // ─────────────────────────────────────────────────────────────

    private String buildExtractionPrompt(String text, String ragContext) {
        String snippet = truncate(text, 2500);

        String contextSection = (ragContext != null && !ragContext.isBlank())
                ? "\n=== HISTORICAL CONTEXT FROM DATABASE ===\n" +
                "(Use this to improve extraction accuracy and inform your recommendation)\n" +
                ragContext + "\n=== END CONTEXT ===\n"
                : "";

        return "You are an enterprise invoice data extractor. Extract fields and return ONLY a JSON object.\n" +
                "Do not add any explanation, markdown, or text outside the JSON.\n\n" +
                "Return this exact JSON structure with real values:\n" +
                "{\"invoice_number\":\"...\",\"vendor_name\":\"...\",\"amount\":0.00," +
                "\"currency\":\"INR\",\"invoice_date\":\"YYYY-MM-DD\",\"due_date\":\"YYYY-MM-DD\"," +
                "\"description\":\"...\",\"confidence\":0.95,\"recommendation\":\"APPROVE\"}\n\n" +
                "Rules:\n" +
                "- amount must be a number only (no commas, no symbols): e.g. 324500.00\n" +
                "- confidence: 0.0 to 1.0 (how complete is the extraction)\n" +
                "- recommendation: APPROVE if vendor is known and amount is consistent with history, else ESCALATE\n" +
                "- vendor_name: lowercase only\n" +
                "- If historical context shows this vendor's typical amount range, use it to validate the invoice amount\n" +
                contextSection +
                "\nInvoice text:\n---\n" + snippet + "\n---\n\nJSON:";
    }

    // ─────────────────────────────────────────────────────────────
    // RESPONSE PARSING
    // ─────────────────────────────────────────────────────────────

    private ExtractionResult parseExtractionResponse(String raw) {
        try {
            String cleaned = raw.replaceAll("(?s)```json|```", "").trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new RuntimeException("No JSON object found in response");
            }
            cleaned = cleaned.substring(start, end + 1);

            JsonNode node = objectMapper.readTree(cleaned);

            String vendor = textOrEmpty(node, "vendor_name");
            double amount = node.path("amount").asDouble(0.0);
            double llmConf = clamp(node.path("confidence").asDouble(0.65));
            String rec = node.path("recommendation").asText("ESCALATE");
            String json = node.toString();

            String invoiceNumber = nullableText(node, "invoice_number");
            String invoiceDate = nullableText(node, "invoice_date");
            String dueDate = nullableText(node, "due_date");
            String description = nullableText(node, "description");

            String[] required = {
                    "invoice_number", "vendor_name", "amount",
                    "currency", "invoice_date", "due_date", "description"
            };

            int present = 0;
            for (String f : required) {
                if (!node.path(f).isMissingNode()
                        && !node.path(f).isNull()
                        && !node.path(f).asText("").isBlank()) {
                    present++;
                }
            }

            double completeness = (double) present / required.length;
            double validation = computeValidation(node);
            boolean approved = !vendor.isBlank()
                    && APPROVED_VENDORS.contains(vendor.toLowerCase().trim());

            return new ExtractionResult(
                    vendor, amount, llmConf, completeness, validation,
                    approved, rec, json, invoiceNumber, invoiceDate, dueDate, description
            );

        } catch (Exception e) {
            log.error("Failed to parse Ollama response: {}", e.getMessage());
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    private double computeValidation(JsonNode node) {
        int checks = 4, passed = 0;
        if (node.path("amount").asDouble(0) > 0) passed++;
        if (node.path("invoice_date").asText("").matches("\\d{4}-\\d{2}-\\d{2}")) passed++;
        if (!node.path("vendor_name").asText("").isBlank()) passed++;
        if (!node.path("invoice_number").asText("").isBlank()) passed++;
        return (double) passed / checks;
    }

    // ─────────────────────────────────────────────────────────────
    // HTTP CALL
    // ─────────────────────────────────────────────────────────────

    private String callOllama(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );

        ResponseEntity<JsonNode> res = restTemplate.postForEntity(
                ollamaBaseUrl + "/api/generate",
                new HttpEntity<>(body, headers),
                JsonNode.class
        );

        if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
            return res.getBody().path("response").asText();
        }

        throw new RuntimeException("Ollama returned HTTP " + res.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────
    // FALLBACK EXTRACTION
    // ─────────────────────────────────────────────────────────────

    private ExtractionResult fallbackExtraction(String text) {
        log.info("Using rule-based fallback extraction");
        String lower = safe(text).toLowerCase();
        double amount = extractAmount(text);
        String vendor = extractVendor(lower);
        boolean approved = APPROVED_VENDORS.contains(vendor.toLowerCase().trim());

        String json = String.format(
                "{\"invoice_number\":\"FALLBACK\",\"vendor_name\":\"%s\",\"amount\":%.2f,\"source\":\"fallback\"}",
                vendor, amount);

        return new ExtractionResult(vendor, amount, 0.55, 0.55, 0.55,
                approved, "ESCALATE", json, "FALLBACK", null, null, "Fallback extraction");
    }

    /**
     * Handles Indian number formatting (e.g. Rs. 3,24,500).
     * Priority: TOTAL AMOUNT line → explicit amount field → largest number.
     */
    private double extractAmount(String text) {
        // First: TOTAL AMOUNT line
        Matcher totalMatcher = Pattern.compile(
                "(?i)total[^\\d]{0,30}[Rr]s\\.?\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        ).matcher(safe(text));
        while (totalMatcher.find()) {
            try {
                double v = Double.parseDouble(totalMatcher.group(1).replace(",", ""));
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }

        // Second: explicit amount field
        Matcher fieldMatcher = Pattern.compile(
                "(?i)\\bamount\\s*[:\\-]\\s*([\\d,]+(?:\\.\\d{1,2})?)"
        ).matcher(safe(text));
        while (fieldMatcher.find()) {
            try {
                double v = Double.parseDouble(fieldMatcher.group(1).replace(",", ""));
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }

        // Fallback: largest number in document
        Matcher m = Pattern.compile("([\\d,]{4,}(?:\\.\\d{1,2})?)").matcher(safe(text));
        double max = 0;
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1).replace(",", ""));
                if (v > max) max = v;
            } catch (NumberFormatException ignored) {}
        }
        return max;
    }

    private String extractVendor(String lower) {
        for (String v : APPROVED_VENDORS) {
            if (lower.contains(v)) return v;
        }
        return "Unknown Vendor";
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return (n.isMissingNode() || n.isNull()) ? "" : n.asText();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String value = n.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private String safe(String v) { return v == null ? "" : v; }

    private String truncate(String text, int maxLen) {
        String safeText = safe(text);
        return safeText.length() <= maxLen ? safeText : safeText.substring(0, maxLen);
    }

    private String toPgVector(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // RESULT RECORD
    // ─────────────────────────────────────────────────────────────

    public record ExtractionResult(
            String vendorName,
            double amount,
            double llmConfidence,
            double completenessScore,
            double validationScore,
            boolean isVendorApproved,
            String recommendation,
            String jsonData,
            String invoiceNumber,
            String invoiceDate,
            String dueDate,
            String description
    ) {
        /**
         * Weighted confidence: LLM quality (50%) + field completeness (30%) + validation (20%).
         */
        public double finalConfidence() {
            return (0.5 * llmConfidence) + (0.3 * completenessScore) + (0.2 * validationScore);
        }
    }
}
