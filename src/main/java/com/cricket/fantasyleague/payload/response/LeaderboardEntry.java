package com.cricket.fantasyleague.payload.response;

public record LeaderboardEntry(
        int rank,
        Integer userId,
        String username,
        String firstname,
        Double totalPoints
) {
}
