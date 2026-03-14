package com.cricket.fantasyleague.service;

import java.util.Map;

import com.cricket.fantasyleague.entity.table.Match;

public interface UserMatchStatsService {

    /**
     * Calculates match points for every user who has a team for the given match.
     * Accepts a pre-computed playerPointsMap to avoid a redundant DB read.
     * Processes users in parallel since they are independent.
     */
    void calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap);
}
