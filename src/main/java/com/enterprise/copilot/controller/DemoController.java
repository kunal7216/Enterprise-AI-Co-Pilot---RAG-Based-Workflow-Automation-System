package com.enterprise.copilot.controller;

import com.enterprise.copilot.repository.WorkflowRepository;
import com.enterprise.copilot.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final WorkflowService workflowService;
    private final WorkflowRepository workflowRepository;

    private static final String SEED_USER = "admin";

    private static final Map<String, String> SEED_INVOICES = new LinkedHashMap<>();

    static {
        SEED_INVOICES.put("INF-001.txt",
                "Invoice Number: INF-001\nVendor Name: Infosys\nDepartment: IT\n" +
                        "Invoice Date: 2025-01-10\nAmount: 8500\n" +
                        "Description: Software development services\nPrevious Decision: APPROVED\n");

        SEED_INVOICES.put("INF-002.txt",
                "Invoice Number: INF-002\nVendor Name: Infosys\nDepartment: IT\n" +
                        "Invoice Date: 2025-02-12\nAmount: 7200\n" +
                        "Description: Monthly IT support services\nPrevious Decision: APPROVED\n");

        SEED_INVOICES.put("INF-003.txt",
                "Invoice Number: INF-003\nVendor Name: Infosys\nDepartment: IT\n" +
                        "Invoice Date: 2025-03-15\nAmount: 45000\n" +
                        "Description: Enterprise software implementation\nPrevious Decision: ESCALATED\n");

        SEED_INVOICES.put("TCS-001.txt",
                "Invoice Number: TCS-001\nVendor Name: TCS\nDepartment: Finance\n" +
                        "Invoice Date: 2025-01-18\nAmount: 9500\n" +
                        "Description: Financial reporting system maintenance\nPrevious Decision: APPROVED\n");

        SEED_INVOICES.put("WIP-001.txt",
                "Invoice Number: WIP-001\nVendor Name: Wipro\nDepartment: HR\n" +
                        "Invoice Date: 2025-01-20\nAmount: 6000\n" +
                        "Description: HR portal support and onboarding\nPrevious Decision: APPROVED\n");
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed() {
        log.info("Demo seed started — {} invoices to process", SEED_INVOICES.size());

        int seeded = 0, skipped = 0, failed = 0, embeddingsGenerated = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> entry : SEED_INVOICES.entrySet()) {
            String filename = entry.getKey();
            String invoiceText = entry.getValue();

            if (workflowRepository.existsByDocumentName(filename)) {
                log.info("Seed skip — already exists: {}", filename);
                skipped++;
                continue;
            }

            try {
                MultipartFile file = new TextMultipartFile(filename, invoiceText);
                var response = workflowService.processDocument(file, SEED_USER);

                boolean embeddingStored = !(
                        "ESCALATED".equals(response.getStatus()) &&
                                "ESCALATED_TO_MANAGER".equals(response.getDecisionType())
                );

                if (embeddingStored) {
                    embeddingsGenerated++;
                } else {
                    log.warn("Seed warning — {} processed but embedding NOT stored " +
                            "(Ollama may be down or invoice escalated due to AI failure)", filename);
                }

                log.info("Seed OK — {} | status={} | decision={} | embedding={}",
                        filename, response.getStatus(), response.getDecisionType(), embeddingStored);
                seeded++;

            } catch (Exception e) {
                log.error("Seed FAILED — {}: {}", filename, e.getMessage());
                errors.add(filename + ": " + e.getMessage());
                failed++;
            }
        }

        log.info("Demo seed complete — seeded={}, skipped={}, failed={}, embeddingsGenerated={}",
                seeded, skipped, failed, embeddingsGenerated);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Seed complete");
        response.put("seeded", seeded);
        response.put("skipped", skipped);
        response.put("failed", failed);
        response.put("embeddingsGenerated", embeddingsGenerated);
        if (!errors.isEmpty()) response.put("errors", errors);

        return ResponseEntity.ok(response);
    }

    // No spring-test dependency needed — implements MultipartFile directly
    private static class TextMultipartFile implements MultipartFile {

        private final String filename;
        private final byte[] content;

        TextMultipartFile(String filename, String content) {
            this.filename = filename;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override public String getName()             { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType()      { return "text/plain"; }
        @Override public boolean isEmpty()            { return content.length == 0; }
        @Override public long getSize()               { return content.length; }
        @Override public byte[] getBytes()            { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(File dest) throws IOException {
            try (FileOutputStream out = new FileOutputStream(dest)) { out.write(content); }
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.write(dest, content);
        }
    }
}
