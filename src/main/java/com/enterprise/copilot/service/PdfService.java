package com.enterprise.copilot.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        try {
            String raw = tika.parseToString(file.getInputStream());
            return raw.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            throw new RuntimeException("PDF extraction failed");
        }
    }
}