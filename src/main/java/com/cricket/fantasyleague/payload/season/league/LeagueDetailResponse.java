package com.cricket.fantasyleague.payload.season.league;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned by {@code GET /api/seasons/leagues/{code}} — the league header
 * plus a lightweight member list (no per-member stats — the leaderboard
 * endpoint provides those).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeagueDetailResponse(
        String code,
        String name,
        Integer maxMembers,
        Integer memberCount,
        Long createdById,
        boolean isCreator,
        LocalDateTime createdAt,
        List<Member> members
) {
    public record Member(
            Long userId,
            String username,
            String firstname,
            boolean isCreator,
            LocalDateTime joinedAt
    ) {
    }
}
