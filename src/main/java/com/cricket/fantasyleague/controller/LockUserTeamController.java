package com.cricket.fantasyleague.controller;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.service.workflow.LiveMatchWorkflowService;

@Component
@RestController
public class LockUserTeamController
{
    private final LiveMatchWorkflowService liveMatchWorkflowService;

    private final Logger logger = LoggerFactory.getLogger(LockUserTeamController.class);

    public LockUserTeamController(LiveMatchWorkflowService liveMatchWorkflowService) {
        this.liveMatchWorkflowService = liveMatchWorkflowService;
    }

    //@Scheduled(cron = "0 0 18 * * *")
    //@PostMapping("/lockteam")
    public void lockTeam()
    {
        logger.info("*****lock team starts****** Timestamp : {}", LocalDateTime.now());
        liveMatchWorkflowService.lockTeamsForCurrentWindow();
    }
}
