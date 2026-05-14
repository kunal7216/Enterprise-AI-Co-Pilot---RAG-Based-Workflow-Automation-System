package com.enterprise.copilot.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that maps float[] <-> PostgreSQL vector(384) column.
 *
 * pgvector expects the format: [0.1,0.2,0.3,...] (no spaces, brackets required)
 * Java's List.toString() produces: [0.1, 0.2, 0.3] (has spaces — rejected by pgvector)
 *
 * This converter handles both directions correctly.
 */
@Converter
public class FloatArrayVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) return null;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            sb.append(attribute[i]);
            if (i < attribute.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new float[0];

        // Strip brackets: "[0.1,0.2,...]" -> "0.1,0.2,..."
        String cleaned = dbData.trim().replaceAll("^\\[|\\]$", "");
        if (cleaned.isBlank()) return new float[0];

        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
