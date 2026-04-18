package com.cricket.fantasyleague.service.api;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.response.LeaderboardEntry;
import com.cricket.fantasyleague.payload.response.LeaderboardPageResponse;

public interface LeaderboardService {

    List<LeaderboardEntry> getRankedLeaderboard();

    LeaderboardPageResponse getRankedLeaderboard(int page, int size, User currentUser);
}
