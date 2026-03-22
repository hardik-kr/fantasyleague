package com.cricket.fantasyleague.service.useroverallpts;

import java.util.Map;

import com.cricket.fantasyleague.entity.table.Match;

public interface UserOverallPtsService {

    /**
     * Updates overall points for all users using the pre-computed match points map,
     * avoiding a redundant DB re-read of UserMatchStats.
     */
    void calcUserOverallPointsData(Match match, Map<Integer, Double> matchPointsByUserId);
}
