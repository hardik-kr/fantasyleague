package com.cricket.fantasyleague.service.api;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.service.masterdata.MasterDataReadService;

@Service
public class FantasyPlayerServiceImpl implements FantasyPlayerService {

    private final MasterDataReadService masterDataReadService;

    public FantasyPlayerServiceImpl(MasterDataReadService masterDataReadService) {
        this.masterDataReadService = masterDataReadService;
    }

    @Override
    public List<PlayerResponse> getAllPlayersWithConfig(Integer leagueId) {
        return masterDataReadService.getAllPlayersWithConfig(leagueId);
    }

    @Override
    public Optional<PlayerResponse> getPlayerWithConfig(Integer leagueId, Integer playerId) {
        return masterDataReadService.getPlayerWithConfig(leagueId, playerId);
    }
}
