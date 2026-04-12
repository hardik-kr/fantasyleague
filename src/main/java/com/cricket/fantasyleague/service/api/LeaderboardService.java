package com.cricket.fantasyleague.service.api;

import java.util.List;

import com.cricket.fantasyleague.payload.response.LeaderboardEntry;

public interface LeaderboardService {

    List<LeaderboardEntry> getRankedLeaderboard();
}
