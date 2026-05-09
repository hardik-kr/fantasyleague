package com.cricket.fantasyleague.payload.season.league;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Paginated response for {@code GET /api/seasons/leagues/{code}/leaderboard}.
 * Mirrors the shape of {@code DailyLeaderboardPageResponse} so the UI can
 * reuse its pagination component.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeagueLeaderboardPageResponse(
        String code,
        List<LeagueLeaderboardEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        LeagueLeaderboardEntry currentUser
) {
}
