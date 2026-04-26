package com.cricket.fantasyleague.payload.season;

public record LeaderboardEntry(
        int rank,
        Long userId,
        String username,
        String firstname,
        Double totalPoints
) {
}
