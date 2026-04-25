package com.cricket.fantasyleague.service.api;

import java.util.List;

import com.cricket.fantasyleague.payload.response.PlayerMatchPointsResponse;

public interface PlayerStatsQueryService {

    /**
     * Returns the match-by-match points history for a single player,
     * sorted by match date (and time) descending.
     */
    List<PlayerMatchPointsResponse> getPointsHistory(Integer playerId);
}
