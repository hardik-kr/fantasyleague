package com.cricket.fantasyleague.cache.dto;

import java.util.Collections;
import java.util.List;

import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;

/**
 * Lightweight projection of {@link DailyUserMatchTeam} for cache storage.
 * Stores only IDs for relationships to avoid serializing full JPA entity graphs
 * (mirrors {@link CachedUserMatchStats} for the season-long pipeline).
 *
 * <p>Heap discipline (per the daily 100K scaling plan):
 * <ul>
 *   <li>{@code playing11Ids} is a {@code List<Integer>} of player ids — never a
 *       list of {@code Player} entities.</li>
 *   <li>The record is immutable; the hot loop allocates one new instance per
 *       user per tick via {@link #withMatchPoints(Double)} and discards the
 *       previous one (no shared mutable state, no defensive copies).</li>
 *   <li>No JPA entity references in the DTO graph, so it is safe to serialize
 *       to Redis under {@code fantasy:dailyMatchTeam:&lt;matchId&gt;}.</li>
 * </ul>
 */
public record CachedDailyUserMatchTeam(
        Long id,
        Long userId,
        String username,
        String firstname,
        Integer matchId,
        Integer captainId,
        Integer viceCaptainId,
        Double matchPoints,
        List<Integer> playing11Ids
) {

    public static CachedDailyUserMatchTeam from(DailyUserMatchTeam entity) {
        return new CachedDailyUserMatchTeam(
                entity.getId(),
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getUser() != null ? entity.getUser().getUsername() : null,
                entity.getUser() != null ? entity.getUser().getFirstname() : null,
                entity.getMatch() != null ? entity.getMatch().getId() : null,
                entity.getCaptainId(),
                entity.getViceCaptainId(),
                entity.getMatchPoints(),
                entity.getPlaying11() != null
                        ? List.copyOf(entity.getPlaying11())
                        : Collections.emptyList()
        );
    }

    /**
     * Returns a copy of this record with a new {@code matchPoints} value.
     * Used by the streaming hot-loop so each user allocates exactly one new
     * record per tick and the previous one is GC-eligible immediately after
     * the chunk callback returns.
     */
    public CachedDailyUserMatchTeam withMatchPoints(Double mp) {
        return new CachedDailyUserMatchTeam(
                id, userId, username, firstname, matchId,
                captainId, viceCaptainId,
                mp,
                playing11Ids);
    }
}
