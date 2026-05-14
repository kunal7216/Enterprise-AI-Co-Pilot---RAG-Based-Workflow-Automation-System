package com.enterprise.copilot.controller;

import com.enterprise.copilot.Dto.AuthRequest;
import com.enterprise.copilot.Dto.AuthResponse;
import com.enterprise.copilot.Dto.*;
import com.enterprise.copilot.entity.User;
import com.enterprise.copilot.enums.Role;
import com.enterprise.copilot.repository.UserRepository;
import com.enterprise.copilot.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and registration")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.USER)
                .isActive(true).build();


        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = (User) authentication.getPrincipal();
            String token = jwtUtil.generateToken(user.getUsername());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid username or password");
        }
    }

    @GetMapping("/test")
    @Operation(summary = "Test endpoint - no auth required")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("API is working! 🚀");
    }
}

