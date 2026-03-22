package com.cricket.fantasyleague.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.service.masterdata.MasterDataConfigService;

@RestController
@RequestMapping("/api/data")
public class MasterDataController {

    private final MasterDataConfigService masterDataConfigService;

    public MasterDataController(MasterDataConfigService masterDataConfigService) {
        this.masterDataConfigService = masterDataConfigService;
    }

    @PostMapping("/fantplayer-config")
    public ResponseEntity<ApiResponse> initializeFantasyPlayerConfig() {
        try {
            masterDataConfigService.initializeFantasyPlayerConfigs();
            return ResponseEntity.ok(new ApiResponse(
                    "Fantasy Player Config initialized successfully",
                    true,
                    HttpStatus.OK.value(),
                    HttpStatus.OK
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(
                            "Error initializing Fantasy Player Config: " + e.getMessage(),
                            false,
                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    ));
        }
    }
}


