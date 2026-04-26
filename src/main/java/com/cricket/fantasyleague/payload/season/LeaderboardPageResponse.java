package com.cricket.fantasyleague.payload.season;

import java.util.List;

public record LeaderboardPageResponse(
        List<LeaderboardEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LeaderboardEntry currentUser
) {
}
