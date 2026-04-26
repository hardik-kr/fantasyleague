package com.cricket.fantasyleague.payload.daily;

public record DailyLeaderboardEntry(
        int rank,
        Long userId,
        String username,
        String firstname,
        Double matchPoints,
        Integer captainId,
        Integer viceCaptainId
) {
}
