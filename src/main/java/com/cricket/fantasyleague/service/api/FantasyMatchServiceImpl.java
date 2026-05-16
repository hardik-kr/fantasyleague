package com.cricket.fantasyleague.service.api;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.service.masterdata.MasterDataReadService;

@Service
public class FantasyMatchServiceImpl implements FantasyMatchService {

    private final MasterDataReadService masterDataReadService;

    public FantasyMatchServiceImpl(MasterDataReadService masterDataReadService) {
        this.masterDataReadService = masterDataReadService;
    }

    @Override
    public List<MatchResponse> getAllMatchesWithTeams() {
        return masterDataReadService.getAllMatchesWithTeams();
    }

    @Override
    public Optional<MatchResponse> getMatchById(Integer matchId) {
        return masterDataReadService.getMatchById(matchId);
    }
}
