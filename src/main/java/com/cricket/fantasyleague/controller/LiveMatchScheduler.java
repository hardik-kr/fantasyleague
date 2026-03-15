package com.cricket.fantasyleague.controller;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.service.workflow.LiveMatchWorkflowService;

/**
 * Unified scheduler for the live-match pipeline.
 *
 * processLiveMatches  — runs the full in-memory pipeline every 25s (ball-by-ball).
 * flushCacheToDB      — persists dirty in-memory data to the database every 45s.
 * lockTeams           — locks user teams at match start time.
 */
@Component
public class LiveMatchScheduler {

    private final LiveMatchWorkflowService liveMatchWorkflowService;

    public LiveMatchScheduler(LiveMatchWorkflowService liveMatchWorkflowService) {
        this.liveMatchWorkflowService = liveMatchWorkflowService;
    }

    // @Scheduled(fixedRate = 25000)
    public void processLiveMatches() {
        liveMatchWorkflowService.processFullPipeline();
    }

    // @Scheduled(fixedRate = 45000, initialDelay = 10000)
    public void flushCacheToDB() {
        liveMatchWorkflowService.flushCacheToDB();
    }

    // @Scheduled(cron = "0 0 18 * * *")
    public void lockTeams() {
        liveMatchWorkflowService.lockTeamsForCurrentWindow();
    }
}
