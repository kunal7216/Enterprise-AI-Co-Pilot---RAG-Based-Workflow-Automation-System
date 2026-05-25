package com.enterprise.copilot.controller;

import com.enterprise.copilot.entity.Workflow;
import com.enterprise.copilot.repository.WorkflowRepository;
import com.enterprise.copilot.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class StreamController {

    private final OllamaService ollamaService;
    private final WorkflowRepository workflowRepository;

    @GetMapping(value = "/{id}/stream-decision", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamDecision(@PathVariable Long id) {
        Workflow wf = workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + id));

        OllamaService.ExtractionResult stub = new OllamaService.ExtractionResult(
                "", 0,
                wf.getConfidenceScore() != null ? wf.getConfidenceScore() : 0.0,
                0, 0, false,
                wf.getDecisionType() != null ? wf.getDecisionType().name() : "UNKNOWN",
                wf.getExtractedData(), null, null, null, null);

        return ollamaService.streamExplanation(
                stub,
                wf.getDecisionType() != null ? wf.getDecisionType().name() : "UNKNOWN",
                wf.getReviewComments() != null ? wf.getReviewComments() : "",
                "").map(
                        token -> ServerSentEvent.<String>builder()
                                .data(token)
                                .build());
    }
}