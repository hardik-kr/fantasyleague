package com.cricket.fantasyleague.payload.daily;

import java.time.LocalDate;
import java.util.List;

/**
 * Per-match summary row for {@code GET /api/daily/history}.
 *
 * <p>{@code rank} is the user's <i>competition rank</i> for this specific
 * match (i.e. {@code count(entries with strictly more points) + 1}), computed
 * with the same logic {@code DailyLeaderboardService} uses for its
 * "current user" badge — so a row's rank here matches what the user sees on
 * the leaderboard hub for the same match. May be {@code null} if the row
 * lacks the data needed to compute it (defensive; not expected in practice).
 *
 * <p>{@code matchDesc} mirrors what the leaderboard / my-team / dashboard
 * dropdowns show ("37th Match", "Eliminator", etc.) and is sourced straight
 * from {@code Match#matchDesc}.
 */
public record DailyMatchHistoryResponse(
        Integer matchId,
        String matchDesc,
        LocalDate date,
        String teamA,
        String teamB,
        Double matchPoints,
        Integer rank,
        Integer captainId,
        Integer viceCaptainId,
        List<Integer> playing11
) {
}
