package com.cricket.fantasyleague.service.workflow;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.service.PlayerPointsService;

@Service
public class TestWorkflowService {

    private final PlayerPointsService playerPointsService;

    public TestWorkflowService(PlayerPointsService playerPointsService) {
        this.playerPointsService = playerPointsService;
    }

    public void calculateTestPoints(Integer matchId) {
        playerPointsService.testPoints(matchId);
    }
}
