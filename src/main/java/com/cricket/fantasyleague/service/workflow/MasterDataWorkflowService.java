package com.cricket.fantasyleague.service.workflow;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.service.MasterDataService;

@Service
public class MasterDataWorkflowService {

    private final MasterDataService masterDataService;

    public MasterDataWorkflowService(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    public void fetchTourPlayers(Integer tourid) {
        masterDataService.fetchTourPlayers(tourid);
    }

    public void fetchLeaguePlayers(String leagueName) {
        masterDataService.fetchLeaguePlayers(leagueName);
    }
}
