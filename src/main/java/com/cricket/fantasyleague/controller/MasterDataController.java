package com.cricket.fantasyleague.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.service.workflow.MasterDataWorkflowService;
import com.cricket.fantasyleague.util.AppConstants;

@RestController
@RequestMapping("/data")
public class MasterDataController
{
    private final MasterDataWorkflowService masterDataWorkflowService;

    public MasterDataController(MasterDataWorkflowService masterDataWorkflowService) {
        this.masterDataWorkflowService = masterDataWorkflowService;
    }

    @PostMapping("/player/tour/{tourid}")
    public ResponseEntity<ApiResponse> fetchTourPlayers(@PathVariable Integer tourid)
    {
        masterDataWorkflowService.fetchTourPlayers(tourid);
        String msg = String.format(AppConstants.masterdata.PLAYER_SUCCESS,tourid) ;
        ApiResponse response = new ApiResponse(msg,true,HttpStatus.CREATED.value(),HttpStatus.CREATED) ;
        return new ResponseEntity<>(response, HttpStatus.CREATED) ;
    }

    @PostMapping("/player/league/{leagueName}")
    public ResponseEntity<ApiResponse> fetchLeaguePlayers(@PathVariable String leagueName)
    {
        masterDataWorkflowService.fetchLeaguePlayers(leagueName);
        String msg = String.format(AppConstants.masterdata.PLAYER_SUCCESS,leagueName) ;
        ApiResponse response = new ApiResponse(msg,true,HttpStatus.CREATED.value(),HttpStatus.CREATED) ;
        return new ResponseEntity<>(response, HttpStatus.CREATED) ;
    }

}
