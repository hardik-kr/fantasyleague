package com.cricket.fantasyleague.service;

import java.util.Map;

import com.cricket.fantasyleague.entity.table.Match;

public interface UserMatchStatsService {

    /**
     * Calculates match points for every user who has a team for the given match.
     * Returns a userId → matchPoints map so the caller can pass it directly
     * to the overall-points step without a redundant DB read.
     */
    Map<Integer, Double> calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap);
}
