package com.enterprise.copilot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;

@Configuration
@OpenAPIDefinition(info = @Info(
        title       = "Enterprise AI Co-Pilot API",
        version     = "1.0.0",
        description = "AI-Powered Workflow Automation System — Final Year B.Tech Project",
        contact     = @Contact(name = "CSE Department")
))
@SecurityScheme(
        name        = "bearerAuth",
        type        = SecuritySchemeType.HTTP,
        scheme      = "bearer",
        bearerFormat = "JWT",
        description = "Paste JWT token from /auth/login"
)
public class AppConfig {

    // 🔥 FIXED: Stable RestTemplate for LLM calls
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {

        return builder
                .setConnectTimeout(Duration.ofSeconds(30))   // was 5 ❌
                .setReadTimeout(Duration.ofSeconds(120))     // was 30 ❌
                .additionalInterceptors(loggingInterceptor())
                .build();
    }

    // 🔍 Optional logging (helps debugging)
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            System.out.println("➡️ Request: " + request.getURI());
            return execution.execute(request, body);
        };
    }

    // 🔥 FIXED: Robust JSON parser for LLM responses
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        // Allow flexible AI responses
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}