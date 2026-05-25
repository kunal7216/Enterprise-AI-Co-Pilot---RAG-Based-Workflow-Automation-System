package com.enterprise.copilot.controller;

import com.enterprise.copilot.service.RagConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes endpoints to get and set the RAG cosine similarity threshold at
 * runtime.
 * No restart required — changes take effect immediately.
 *
 * GET /api/v1/config/rag-threshold → returns current threshold
 * POST /api/v1/config/rag-threshold → sets new threshold (0.1 to 1.0)
 * POST /api/v1/config/rag-threshold/reset → resets to application.properties
 * default
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class RagConfigController {

    private final RagConfigService ragConfigService;

    @GetMapping("/rag-threshold")
    public ResponseEntity<Map<String, Object>> getThreshold() {
        return ResponseEntity.ok(Map.of(
                "threshold", ragConfigService.getThreshold(),
                "description", "Cosine similarity threshold for pgvector RAG retrieval"));
    }

    @PostMapping("/rag-threshold")
    public ResponseEntity<?> setThreshold(@RequestBody Map<String, Double> body) {
        Double threshold = body.get("threshold");
        if (threshold == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'threshold' field in request body"));
        }
        try {
            double updated = ragConfigService.setThreshold(threshold);
            return ResponseEntity.ok(Map.of(
                    "threshold", updated,
                    "status", "updated"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rag-threshold/reset")
    public ResponseEntity<Map<String, Object>> resetThreshold() {
        ragConfigService.reset();
        return ResponseEntity.ok(Map.of(
                "threshold", ragConfigService.getThreshold(),
                "status", "reset to default"));
    }
}