package com.enterprise.copilot.kafka;

import com.enterprise.copilot.enums.WorkflowStatus;
import com.enterprise.copilot.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowRetryConsumer {

    private final WorkflowRepository workflowRepository;
    private final WorkflowKafkaProducer producer;

    @Value("${workflow.retry.max-attempts:3}")
    private int maxAttempts;

    // In-memory retry counter — resets on restart (acceptable for this scope)
    private final Map<Long, Integer> retryCount = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${workflow.kafka.topic.failed}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleFailed(String message) {
        Long workflowId = Long.parseLong(message.trim());
        int attempts = retryCount.getOrDefault(workflowId, 0) + 1;
        retryCount.put(workflowId, attempts);

        log.info("Retry attempt {}/{} for workflow #{}", attempts, maxAttempts, workflowId);

        if (attempts > maxAttempts) {
            log.warn("Workflow #{} exceeded max retries — routing to DLQ", workflowId);
            updateStatus(workflowId, WorkflowStatus.FAILED);
            producer.publishDlq(workflowId);
            retryCount.remove(workflowId);
            return;
        }

        // Exponential backoff: 2s, 4s, 8s
        long delayMs = (long) Math.pow(2, attempts) * 1000L;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        updateStatus(workflowId, WorkflowStatus.PROCESSING);
        log.info("Workflow #{} status reset to PROCESSING for retry attempt {}",
                workflowId, attempts);
    }

    @KafkaListener(topics = "${workflow.kafka.topic.dlq}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleDlq(String message) {
        Long workflowId = Long.parseLong(message.trim());
        log.error("Workflow #{} landed in DLQ after all retries exhausted", workflowId);
        updateStatus(workflowId, WorkflowStatus.FAILED);
    }

    private void updateStatus(Long workflowId, WorkflowStatus status) {
        workflowRepository.findById(workflowId).ifPresent(wf -> {
            wf.setStatus(status);
            workflowRepository.save(wf);
        });
    }
}