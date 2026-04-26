package com.cricket.fantasyleague.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.service.benchmark.PipelineBenchmarkService;

/**
 * Load-test endpoints for verifying pipeline scalability.
 *
 * Usage flow:
 *   1. POST /api/loadtest/seed?userCount=100      → creates test data
 *   2. POST /api/loadtest/benchmark?iterations=5   → runs pipeline, returns timing
 *   3. DELETE /api/loadtest/cleanup                → removes all test data
 *
 * Repeat step 1-3 with userCount=1000, then 10000 to verify scaling.
 */
@RestController
@RequestMapping("/api/loadtest")
public class LoadTestController {

    private final PipelineBenchmarkService benchmarkService;

    public LoadTestController(PipelineBenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed(
            @RequestParam(defaultValue = "100") int userCount) {
        return ResponseEntity.ok(benchmarkService.seed(userCount));
    }

    @PostMapping("/benchmark")
    public ResponseEntity<Map<String, Object>> benchmark(
            @RequestParam(defaultValue = "800000") int matchId,
            @RequestParam(defaultValue = "5") int iterations) {
        return ResponseEntity.ok(benchmarkService.benchmark(matchId, iterations));
    }

    @PostMapping("/full")
    public ResponseEntity<Map<String, Object>> fullTest(
            @RequestParam(defaultValue = "100") int userCount,
            @RequestParam(defaultValue = "5") int iterations) {
        Map<String, Object> seedResult = benchmarkService.seed(userCount);
        int matchId = (int) seedResult.get("matchId");

        Map<String, Object> benchResult = benchmarkService.benchmark(matchId, iterations);

        Map<String, Object> cleanupResult = benchmarkService.cleanup();

        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("seed", seedResult);
        combined.put("benchmark", benchResult);
        combined.put("cleanup", cleanupResult);
        return ResponseEntity.ok(combined);
    }

    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> memory() {
        return ResponseEntity.ok(benchmarkService.memoryReport());
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanup() {
        return ResponseEntity.ok(benchmarkService.cleanup());
    }

    // ── Daily Challenge benchmark surface ──
    //
    // Mirrors the season-long endpoints above but for the daily pipeline.
    // Recommended flow for a 100K parity run:
    //   1. POST /api/loadtest/seed?userCount=100000          — base users + match
    //   2. POST /api/loadtest/daily/seed?userCount=100000    — daily teams
    //   3. POST /api/loadtest/daily/benchmark?iterations=60  — warm + 60 ticks + flush
    //   4. POST /api/loadtest/daily/evict                    — assert heap back to baseline
    //   5. DELETE /api/loadtest/daily/cleanup                — drop daily test rows
    //   6. DELETE /api/loadtest/cleanup                      — drop base test data
    //
    // Run again with userCount=20000 (avg case) and userCount=0 (empty case)
    // to validate the workload-asymmetry behaviour from the scaling plan.

    @PostMapping("/daily/seed")
    public ResponseEntity<Map<String, Object>> seedDaily(
            @RequestParam(defaultValue = "100") int userCount) {
        return ResponseEntity.ok(benchmarkService.seedDaily(userCount));
    }

    @PostMapping("/daily/benchmark")
    public ResponseEntity<Map<String, Object>> dailyBenchmark(
            @RequestParam(defaultValue = "800000") int matchId,
            @RequestParam(defaultValue = "5") int iterations) {
        return ResponseEntity.ok(benchmarkService.dailyBenchmark(matchId, iterations));
    }

    @PostMapping("/daily/evict")
    public ResponseEntity<Map<String, Object>> dailyEvict(
            @RequestParam(defaultValue = "800000") int matchId) {
        return ResponseEntity.ok(benchmarkService.dailyEvict(matchId));
    }

    @DeleteMapping("/daily/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupDaily() {
        return ResponseEntity.ok(benchmarkService.cleanupDaily());
    }
}
