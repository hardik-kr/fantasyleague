package com.cricket.fantasyleague.controller;

import com.cricket.fantasyleague.payload.dto.SystemInfoResponse;
import com.cricket.fantasyleague.service.HealthService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping({"/", "/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("message", "Fantasy League is running", "status", true));
    }

    @GetMapping("/health/system")
    public ResponseEntity<SystemInfoResponse> systemInfo() {
        return ResponseEntity.ok(healthService.getSystemInfo());
    }
}
