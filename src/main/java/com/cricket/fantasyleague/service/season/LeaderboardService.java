package com.cricket.fantasyleague.service.season;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.season.LeaderboardEntry;
import com.cricket.fantasyleague.payload.season.LeaderboardPageResponse;

public interface LeaderboardService {

    List<LeaderboardEntry> getRankedLeaderboard();

    LeaderboardPageResponse getRankedLeaderboard(int page, int size, User currentUser);
}
