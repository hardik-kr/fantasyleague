package com.cricket.fantasyleague.payload.season.league;

import java.time.LocalDateTime;

/**
 * Compact representation of a league for the "My Leagues" hub page —
 * just enough fields to render a card without fetching the full detail.
 */
public record LeagueSummary(
        String code,
        String name,
        Integer maxMembers,
        Integer memberCount,
        boolean isCreator,
        LocalDateTime joinedAt,
        Integer myRank,
        Double myPoints
) {
}
