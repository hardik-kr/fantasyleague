package com.cricket.fantasyleague.payload.response;

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
