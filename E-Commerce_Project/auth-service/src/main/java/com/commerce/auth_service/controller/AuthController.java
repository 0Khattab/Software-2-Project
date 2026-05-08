package com.commerce.auth_service.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.commerce.auth_service.dto.*;
import com.commerce.auth_service.service.AuthServiceImp;
import com.commerce.auth_service.service.PasswordResetServiceImp;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthServiceImp authService;
    @Autowired
    private PasswordResetServiceImp passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build(); // 204
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @RequestHeader("User-Id") String userId) {
        authService.logoutAll(userId);
        return ResponseEntity.noContent().build(); // 204
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.forgotPassword(request.getEmail());

        return ResponseEntity.ok(Map.of(
                "message",
                "If this email is registered, you will receive a reset link shortly."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request);

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully. Please log in."));
    }

}
