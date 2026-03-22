package com.cricket.fantasyleague.service.workflow;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.service.playerpoints.LiveMatchPlayerPointsService;

@Service
public class TestWorkflowService {

    private final LiveMatchPlayerPointsService playerPointsService;

    public TestWorkflowService(LiveMatchPlayerPointsService playerPointsService) {
        this.playerPointsService = playerPointsService;
    }

    public void calculateTestPoints(Integer matchId) {
        playerPointsService.testPoints(matchId);
    }
}
