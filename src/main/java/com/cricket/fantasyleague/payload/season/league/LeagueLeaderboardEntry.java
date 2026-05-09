package com.cricket.fantasyleague.payload.season.league;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of a private-league leaderboard. {@code rank} is the global
 * rank within the league (1-based, ties broken by user-id ordering from
 * the underlying SQL ORDER BY).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeagueLeaderboardEntry(
        int rank,
        Long userId,
        String username,
        String firstname,
        Double totalPoints,
        boolean isCreator
) {
}
