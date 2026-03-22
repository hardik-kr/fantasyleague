package com.cricket.fantasyleague.service.playerpoints;

import java.util.Map;

import com.cricket.fantasyleague.entity.table.Match;

public interface LiveMatchPlayerPointsService {

    void testPoints(Integer id);

    /**
     * Calculates fantasy points for all playing-XI players in the given match.
     * Uses cached scorecard (avoids redundant HTTP calls within the TTL window).
     *
     * @return playerId → points map, ready for downstream UserMatchStats calculation
     */
    Map<Integer, Double> calculatePlayerPoints(Match match);
}
