package com.cricket.fantasyleague.service.daily;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.daily.DailyLeaderboardPageResponse;

/**
 * Per-match Daily Challenge leaderboard. There is no overall/season leaderboard
 * for daily — every match is scored in isolation.
 */
public interface DailyLeaderboardService {

    DailyLeaderboardPageResponse getMatchLeaderboard(Integer matchId,
                                                     int page,
                                                     int size,
                                                     User currentUser);
}
