package com.enterprise.copilot.controller;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Expenditure Dashboard Controller
 * Returns real financial data from uploaded invoices
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard", description = "Expenditure and analytics dashboard APIs")
@SecurityRequirement(name = "bearerAuth")
public class ExpenditureController {

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;

    /**
     * Main expenditure endpoint — powers ALL dashboard charts
     */
    @GetMapping("/expenditure")
    @Operation(summary = "Get expenditure data",
            description = "Returns month-wise, year-wise, vendor-wise, department-wise expenditure from real invoice data")
    public ResponseEntity<Map<String, Object>> getExpenditure() {

        List<Workflow> allWorkflows = workflowRepository.findAll();

        // ── Accumulators ──────────────────────────────────────────────────
        Map<String, Double> monthWise  = new LinkedHashMap<>();
        Map<String, Double> yearWise   = new LinkedHashMap<>();
        Map<String, Double> vendorWise = new LinkedHashMap<>();
        Map<String, Double> deptWise   = new LinkedHashMap<>();
        Map<String, Integer> statusWise    = new LinkedHashMap<>();
        Map<String, Integer> decisionWise  = new LinkedHashMap<>();
        List<Double> confidenceList    = new ArrayList<>();
        List<Long>   processingTimes   = new ArrayList<>();

        // Initialize months
        String[] months = {"Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec"};
        for (String m : months) monthWise.put(m, 0.0);

        // Initialize departments
        deptWise.put("IT", 0.0);
        deptWise.put("HR", 0.0);
        deptWise.put("Finance", 0.0);
        deptWise.put("Operations", 0.0);
        deptWise.put("Marketing", 0.0);

        double totalSpend = 0.0;
        int fraudCount = 0;
        int duplicateCount = 0;

        for (Workflow w : allWorkflows) {
            // Status counts
            String status = w.getStatus() != null ? w.getStatus().name() : "UNKNOWN";
            statusWise.merge(status, 1, Integer::sum);

            // Decision counts
            if (w.getDecisionType() != null) {
                decisionWise.merge(w.getDecisionType().name(), 1, Integer::sum);
            }

            // Confidence scores
            if (w.getConfidenceScore() != null) {
                confidenceList.add(w.getConfidenceScore());
            }

            // Processing times
            if (w.getProcessingTimeMs() != null) {
                processingTimes.add(w.getProcessingTimeMs());
            }

            // Parse extracted data
            String extractedJson = w.getExtractedData();
            if (extractedJson == null || extractedJson.contains("\"source\":\"fallback\"")) {
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(extractedJson);
                double amount = node.path("amount").asDouble(0.0);
                String vendor = node.path("vendor_name").asText("Unknown").trim();
                String invoiceDate = node.path("invoice_date").asText("");
                String department = node.path("department").asText("").trim();
                boolean isDuplicate = node.path("is_duplicate").asBoolean(false);
                int fraudScore = node.path("fraud_score").asInt(0);

                if (amount <= 0) continue;

                totalSpend += amount;

                // Month-wise
                if (!invoiceDate.isEmpty() && invoiceDate.length() >= 7) {
                    try {
                        int monthNum = Integer.parseInt(invoiceDate.substring(5, 7));
                        if (monthNum >= 1 && monthNum <= 12) {
                            String monthName = months[monthNum - 1];
                            monthWise.merge(monthName, amount, Double::sum);
                        }
                    } catch (NumberFormatException ignored) {}
                } else if (w.getCreatedAt() != null) {
                    // Use upload date if invoice date not available
                    int monthNum = w.getCreatedAt().getMonthValue();
                    String monthName = months[monthNum - 1];
                    monthWise.merge(monthName, amount, Double::sum);
                }

                // Year-wise
                String year;
                if (!invoiceDate.isEmpty() && invoiceDate.length() >= 4) {
                    year = invoiceDate.substring(0, 4);
                } else if (w.getCreatedAt() != null) {
                    year = String.valueOf(w.getCreatedAt().getYear());
                } else {
                    year = "2025";
                }
                yearWise.merge(year, amount, Double::sum);

                // Vendor-wise
                if (!vendor.isEmpty() && !vendor.equals("Unknown")) {
                    String vendorKey = capitalizeFirst(vendor);
                    vendorWise.merge(vendorKey, amount, Double::sum);
                }

                // Department-wise
                if (!department.isEmpty()) {
                    deptWise.merge(capitalizeFirst(department), amount, Double::sum);
                } else {
                    // Auto-assign department based on vendor
                    String dept = guessDepartment(vendor);
                    deptWise.merge(dept, amount, Double::sum);
                }

                if (isDuplicate) duplicateCount++;
                if (fraudScore >= 60) fraudCount++;

            } catch (Exception e) {
                log.debug("Could not parse extracted data for workflow {}: {}", w.getId(), e.getMessage());
            }
        }

        // ── Confidence Distribution (10 buckets: 0-0.1, 0.1-0.2 ... 0.9-1.0) ──
        int[] confDist = new int[10];
        for (double c : confidenceList) {
            int bucket = Math.min((int)(c * 10), 9);
            confDist[bucket]++;
        }

        // ── Processing Time Distribution ──────────────────────────────────
        int[] timeDist = new int[6]; // <1s, 1-2s, 2-5s, 5-10s, 10-30s, >30s
        for (long t : processingTimes) {
            if      (t < 1000)  timeDist[0]++;
            else if (t < 2000)  timeDist[1]++;
            else if (t < 5000)  timeDist[2]++;
            else if (t < 10000) timeDist[3]++;
            else if (t < 30000) timeDist[4]++;
            else                timeDist[5]++;
        }

        // ── Top Vendors (sorted) ──────────────────────────────────────────
        List<Map<String, Object>> topVendors = vendorWise.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vendor", e.getKey());
                    m.put("amount", e.getValue());
                    m.put("formatted", formatAmount(e.getValue()));
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());

        // ── Average Confidence ────────────────────────────────────────────
        double avgConf = confidenceList.stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        double avgTime = processingTimes.stream()
                .mapToLong(Long::longValue)
                .average().orElse(0.0) / 1000.0;

        double automationRate = allWorkflows.isEmpty() ? 0.0 :
                (double) statusWise.getOrDefault("COMPLETED", 0) / allWorkflows.size() * 100;

        // ── Build Response ────────────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("monthWise", monthWise);
        response.put("yearWise", yearWise);
        response.put("vendorWise", vendorWise);
        response.put("departmentWise", deptWise);
        response.put("statusWise", statusWise);
        response.put("decisionWise", decisionWise);
        response.put("confidenceDistribution", confDist);
        response.put("processingTimeDistribution", timeDist);
        response.put("topVendors", topVendors);
        response.put("totalSpend", totalSpend);
        response.put("totalSpendFormatted", formatAmount(totalSpend));
        response.put("totalWorkflows", allWorkflows.size());
        response.put("fraudCount", fraudCount);
        response.put("duplicateCount", duplicateCount);
        response.put("avgConfidenceScore", Math.round(avgConf * 1000.0) / 1000.0);
        response.put("avgProcessingTimeSeconds", Math.round(avgTime * 100.0) / 100.0);
        response.put("automationRate", Math.round(automationRate * 10.0) / 10.0);

        return ResponseEntity.ok(response);
    }

    /**
     * NLP Query endpoint — natural language to data
     */
    @PostMapping("/query")
    @Operation(summary = "Natural language query",
            description = "Query invoice data in plain English e.g. 'show all infosys invoices above 50000'")
    public ResponseEntity<Map<String, Object>> nlpQuery(
            @RequestBody Map<String, String> request) {

        String query = request.getOrDefault("query", "").toLowerCase().trim();
        List<Workflow> allWorkflows = workflowRepository.findAll();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Workflow w : allWorkflows) {
            if (w.getExtractedData() == null) continue;
            try {
                JsonNode node = objectMapper.readTree(w.getExtractedData());
                double amount = node.path("amount").asDouble(0);
                String vendor = node.path("vendor_name").asText("").toLowerCase();
                String date   = node.path("invoice_date").asText("");
                String status = w.getStatus() != null ? w.getStatus().name().toLowerCase() : "";

                boolean matches = matchesQuery(query, vendor, amount, date, status);
                if (matches) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", w.getId());
                    item.put("vendor", node.path("vendor_name").asText());
                    item.put("amount", amount);
                    item.put("amountFormatted", formatAmount(amount));
                    item.put("date", date);
                    item.put("status", w.getStatus() != null ? w.getStatus().name() : "");
                    item.put("confidence", w.getConfidenceScore());
                    results.add(item);
                }
            } catch (Exception ignored) {}
        }

        // Sort by amount descending
        results.sort((a, b) -> Double.compare(
                (Double) b.getOrDefault("amount", 0.0),
                (Double) a.getOrDefault("amount", 0.0)));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("resultCount", results.size());
        response.put("results", results);
        response.put("totalAmount", results.stream()
                .mapToDouble(r -> (Double) r.getOrDefault("amount", 0.0)).sum());

        return ResponseEntity.ok(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private boolean matchesQuery(String query, String vendor,
                                 double amount, String date, String status) {
        // Vendor match
        if (query.contains(vendor) && !vendor.isEmpty()) return true;

        // Amount conditions
        if (query.contains("above") || query.contains("greater")) {
            double threshold = extractNumber(query);
            if (threshold > 0 && amount > threshold) return true;
        }
        if (query.contains("below") || query.contains("less")) {
            double threshold = extractNumber(query);
            if (threshold > 0 && amount < threshold) return true;
        }

        // Status match
        if (query.contains("approved") && status.contains("completed")) return true;
        if (query.contains("escalated") && status.contains("escalated")) return true;
        if (query.contains("rejected") && status.contains("rejected")) return true;

        // Month match
        String[] months = {"jan","feb","mar","apr","may","jun",
                "jul","aug","sep","oct","nov","dec"};
        for (int i = 0; i < months.length; i++) {
            if (query.contains(months[i]) && date.contains(String.format("-%02d-", i+1)))
                return true;
        }

        return false;
    }

    private double extractNumber(String query) {
        String[] tokens = query.split("\\s+");
        for (String t : tokens) {
            try {
                String cleaned = t.replaceAll("[^0-9.]", "");
                if (!cleaned.isEmpty()) return Double.parseDouble(cleaned);
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    private String guessDepartment(String vendor) {
        vendor = vendor.toLowerCase();
        if (vendor.contains("infosys") || vendor.contains("tcs") ||
                vendor.contains("wipro") || vendor.contains("ibm") ||
                vendor.contains("microsoft") || vendor.contains("oracle") ||
                vendor.contains("google") || vendor.contains("amazon"))
            return "IT";
        if (vendor.contains("deloitte") || vendor.contains("pwc") ||
                vendor.contains("kpmg") || vendor.contains("ey"))
            return "Finance";
        if (vendor.contains("accenture") || vendor.contains("capgemini"))
            return "Operations";
        return "IT"; // default
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String formatAmount(double amount) {
        if (amount >= 10000000) return String.format("₹%.1fCr", amount / 10000000);
        if (amount >= 100000)   return String.format("₹%.1fL", amount / 100000);
        if (amount >= 1000)     return String.format("₹%.1fK", amount / 1000);
        return String.format("₹%.0f", amount);
    }
}