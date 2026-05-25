package com.enterprise.copilot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages the configurable cosine similarity threshold for RAG retrieval.
 * Threshold is stored in-memory — resets to default on restart.
 * Default value is loaded from application.properties.
 */
@Service
public class RagConfigService {

    @Value("${rag.similarity.threshold:0.75}")
    private double defaultThreshold;

    private volatile double activeThreshold = -1;

    public double getThreshold() {
        return activeThreshold < 0 ? defaultThreshold : activeThreshold;
    }

    public double setThreshold(double threshold) {
        if (threshold < 0.1 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "Threshold must be between 0.1 and 1.0, got: " + threshold);
        }
        this.activeThreshold = threshold;
        return this.activeThreshold;
    }

    public void reset() {
        this.activeThreshold = -1;
    }
}