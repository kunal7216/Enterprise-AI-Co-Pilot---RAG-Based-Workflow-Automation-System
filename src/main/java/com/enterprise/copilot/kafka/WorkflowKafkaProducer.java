package com.enterprise.copilot.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${workflow.kafka.topic.failed}")
    private String failedTopic;

    @Value("${workflow.kafka.topic.dlq}")
    private String dlqTopic;

    public void publishFailed(Long workflowId) {
        kafkaTemplate.send(failedTopic, workflowId.toString());
        log.info("Published workflow #{} to {}", workflowId, failedTopic);
    }

    public void publishDlq(Long workflowId) {
        kafkaTemplate.send(dlqTopic, workflowId.toString());
        log.info("Published workflow #{} to {}", workflowId, dlqTopic);
    }
}