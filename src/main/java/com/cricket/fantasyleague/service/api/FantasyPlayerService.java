package com.cricket.fantasyleague.service.api;

import java.util.List;
import java.util.Optional;

import com.cricket.fantasyleague.payload.response.PlayerResponse;

public interface FantasyPlayerService {

    List<PlayerResponse> getAllPlayersWithConfig(Integer leagueId);

    Optional<PlayerResponse> getPlayerWithConfig(Integer leagueId, Integer playerId);
}
