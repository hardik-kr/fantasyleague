package com.cricket.fantasyleague.controller;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.service.workflow.LiveMatchWorkflowService;

/**
 * Single unified scheduler that replaces the old separate
 * PlayerPointsController, UserMatchPointsController, and UserOverallPointsController.
 *
 * Runs the full pipeline (player points → user match points → user overall points)
 * in one pass per cycle, sharing the cached scorecard and computed player-points map.
 */
@Component
public class LiveMatchScheduler {

    private final LiveMatchWorkflowService liveMatchWorkflowService;

    public LiveMatchScheduler(LiveMatchWorkflowService liveMatchWorkflowService) {
        this.liveMatchWorkflowService = liveMatchWorkflowService;
    }

    // @Scheduled(fixedRate = 60000)
    public void processLiveMatches() {
        liveMatchWorkflowService.processFullPipeline();
    }

    // @Scheduled(cron = "0 0 18 * * *")
    public void lockTeams() {
        liveMatchWorkflowService.lockTeamsForCurrentWindow();
    }
}
