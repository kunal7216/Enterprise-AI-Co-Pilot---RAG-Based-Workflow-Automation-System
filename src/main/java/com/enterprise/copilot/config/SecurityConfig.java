package com.enterprise.copilot.config;

import com.enterprise.copilot.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> {})

                // Disable CSRF (not needed for REST APIs)
                .csrf(AbstractHttpConfigurer::disable)

                // Authorize requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                new AntPathRequestMatcher("/"),                     // root
                                new AntPathRequestMatcher("/api/v1/auth/**"),       // ✅ FIXED: was /api/auth/**
                                new AntPathRequestMatcher("/swagger-ui/**"),        // swagger UI
                                new AntPathRequestMatcher("/swagger-ui.html"),
                                new AntPathRequestMatcher("/v3/api-docs/**"),
                                new AntPathRequestMatcher("/api-docs/**"),
                                new AntPathRequestMatcher("/actuator/**"),          // health
                                new AntPathRequestMatcher("/h2-console/**"),        // optional
                                new AntPathRequestMatcher("/error"),
                                new AntPathRequestMatcher("/favicon.ico"),
                                new AntPathRequestMatcher("/api/v1/demo/**")
                        ).permitAll()
                        .anyRequest().authenticated() // everything else protected
                )

                // Allow H2 console frames (optional)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )

                // Stateless session (JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authentication provider
                .authenticationProvider(authenticationProvider)

                // Add JWT filter before Spring auth filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
