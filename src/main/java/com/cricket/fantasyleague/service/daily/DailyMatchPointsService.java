package com.cricket.fantasyleague.service.daily;

import java.util.Map;

/**
 * Recomputes Daily Challenge {@code match_points} for every locked daily team
 * of a match, using the live player-points map produced upstream by
 * {@code LiveMatchPlayerPointsService}.
 *
 * <p>Captain × 2, Vice-captain × 1.5, others × 1. No boosters in daily mode.
 *
 * <p>Pure recompute from {@code player_points}: naturally idempotent and safe
 * to call every tick.
 */
public interface DailyMatchPointsService {

    /**
     * @param matchId         the live match id
     * @param playerPointsMap player_id → total accumulated points for the match
     */
    void updateForLiveMatch(Integer matchId, Map<Integer, Double> playerPointsMap);
}
