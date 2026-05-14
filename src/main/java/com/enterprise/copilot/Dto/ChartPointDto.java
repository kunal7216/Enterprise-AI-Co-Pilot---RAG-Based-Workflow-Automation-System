package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chart data point for dashboard status/decision charts.
 * Previously was incorrectly named "chartPoint" (lowercase) — fixed to ChartPointDto.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartPointDto {
    private String label;
    private Long   value;
}
