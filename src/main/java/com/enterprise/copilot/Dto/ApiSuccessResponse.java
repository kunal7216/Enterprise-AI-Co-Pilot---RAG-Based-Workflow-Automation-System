package com.enterprise.copilot.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSuccessResponse {
    private boolean success;
    private String message;
    private Object data;

    public static ApiSuccessResponse of(String message) {
        return ApiSuccessResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiSuccessResponse of(String message, Object data) {
        return ApiSuccessResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
}