package com.cricket.fantasyleague.service.usermatchstats;

import java.util.Map;

import com.cricket.fantasyleague.entity.table.Match;

public interface UserMatchStatsService {

    /**
     * Calculates match points for every user who has a team for the given match
     * and writes the updated matchpoints straight back to the cache in bounded
     * chunks. The downstream overall-points stage reads the freshly-written
     * matchpoints from the cache via {@code snapshotLiveMatchpointsByUser()};
     * no intermediate Map is returned.
     */
    void calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap);
}
