package com.enterprise.copilot.Dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
}