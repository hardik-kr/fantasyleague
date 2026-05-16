package com.cricket.fantasyleague.controller.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.service.masterdata.MasterDataReadService;

/**
 * Ops endpoints for master match/player read cache. Requires {@code ADMIN} (see
 * {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/admin/cache")
public class MasterCacheAdminController {

    private final MasterDataReadService masterDataReadService;

    public MasterCacheAdminController(MasterDataReadService masterDataReadService) {
        this.masterDataReadService = masterDataReadService;
    }

    /** Reloads matches and every cached league’s player list from DB into the store. */
    @PostMapping("/master/reload")
    public ResponseEntity<Map<String, Object>> reloadMasterCache() {
        masterDataReadService.reloadMatchesAndCachedPlayerLeagues();
        return ResponseEntity.ok(Map.of(
                "status", "reloaded",
                "masterCacheEnabled", masterDataReadService.isEnabled()));
    }

    /** Clears master cache; subsequent API reads repopulate from DB. */
    @PostMapping("/master/evict")
    public ResponseEntity<Map<String, Object>> evictMasterCache() {
        masterDataReadService.evictAll();
        return ResponseEntity.ok(Map.of(
                "status", "evicted",
                "masterCacheEnabled", masterDataReadService.isEnabled()));
    }
}
