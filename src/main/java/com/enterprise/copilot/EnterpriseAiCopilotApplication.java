package com.enterprise.copilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EnterpriseAiCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAiCopilotApplication.class, args);
    }
}