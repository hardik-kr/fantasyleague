package com.cricket.fantasyleague.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.jwtdto.JwtRequest;
import com.cricket.fantasyleague.payload.jwtdto.JwtResponse;
import com.cricket.fantasyleague.service.workflow.AuthWorkflowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthWorkflowService authWorkflowService;

    public AuthController(AuthWorkflowService authWorkflowService) {
        this.authWorkflowService = authWorkflowService;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody JwtRequest request) {
        return authWorkflowService.login(request);
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> fetchPlayers(@Valid @RequestBody UserDto request) {
        return authWorkflowService.signup(request);
    }
}
