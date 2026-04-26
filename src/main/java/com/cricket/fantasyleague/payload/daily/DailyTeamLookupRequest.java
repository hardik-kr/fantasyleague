package com.cricket.fantasyleague.payload.daily;

/**
 * Request body for {@code POST /api/daily/team} — view a (user, match) pair's
 * locked team. Mirrors season-long's {@code UserTeamRequest}: both ids are
 * carried in the body rather than the URL, which keeps the surface uniform
 * with {@code POST /api/seasons/team} and lets the same call shape serve
 * both "view my team for past match X" and "view user Y's team" (e.g. from
 * a leaderboard click-through).
 */
public record DailyTeamLookupRequest(Long userId, Integer matchId) {
}
