package com.cricket.fantasyleague.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.config.AppConfig;
import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.dto.OtpVerifyRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetCompleteRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetInitiateRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetVerifyRequest;
import com.cricket.fantasyleague.payload.dto.PasswordResetVerifyResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.jwtdto.JwtRequest;
import com.cricket.fantasyleague.payload.jwtdto.JwtResponse;
import com.cricket.fantasyleague.payload.response.AppConfigResponse;
import com.cricket.fantasyleague.service.workflow.AuthWorkflowService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthWorkflowService authWorkflowService;
    private final AppConfig appConfig;

    public AuthController(AuthWorkflowService authWorkflowService, AppConfig appConfig) {
        this.authWorkflowService = authWorkflowService;
        this.appConfig = appConfig;
    }

    @GetMapping("/config")
    public ResponseEntity<AppConfigResponse> getConfig() {
        return ResponseEntity.ok(new AppConfigResponse(
                appConfig.getActiveLeagueId(),
                appConfig.getName(),
                appConfig.getYear(),
                appConfig.getStatus(),
                appConfig.getTotalTransfer(),
                appConfig.getTotalBooster()));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(
            @RequestBody JwtRequest request,
            HttpServletResponse response) {
        return authWorkflowService.login(request, response);
    }

    @PostMapping("/signup/initiate")
    public ResponseEntity<ApiResponse> initiateSignup(@Valid @RequestBody UserDto request) {
        return authWorkflowService.initiateSignup(request);
    }

    @PostMapping("/signup/verify")
    public ResponseEntity<JwtResponse> verifySignup(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletResponse response) {
        return authWorkflowService.verifySignup(request, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return authWorkflowService.refresh(request, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        return authWorkflowService.logout(request, response);
    }

    @PostMapping("/password-reset/initiate")
    public ResponseEntity<ApiResponse> initiatePasswordReset(
            @Valid @RequestBody PasswordResetInitiateRequest request) {
        return authWorkflowService.initiatePasswordReset(request);
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<PasswordResetVerifyResponse> verifyPasswordReset(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        return authWorkflowService.verifyPasswordReset(request);
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<ApiResponse> completePasswordReset(
            @Valid @RequestBody PasswordResetCompleteRequest request) {
        return authWorkflowService.completePasswordReset(request);
    }
}
