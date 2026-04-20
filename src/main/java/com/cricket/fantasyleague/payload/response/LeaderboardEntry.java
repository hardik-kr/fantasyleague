package com.cricket.fantasyleague.payload.response;

public record LeaderboardEntry(
        int rank,
        Long userId,
        String username,
        String firstname,
        Double totalPoints
) {
}
