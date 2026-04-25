package com.cricket.fantasyleague.service.useroverallpts;

import com.cricket.fantasyleague.entity.table.Match;

public interface UserOverallPtsService {

    /**
     * Updates overall points for every active user by summing their committed
     * non-live total with the current live-match points snapshot read directly
     * from the cache. Matches are streamed in bounded chunks so peak heap
     * footprint is O(chunk), independent of total user count.
     */
    void calcUserOverallPointsData(Match match);
}
