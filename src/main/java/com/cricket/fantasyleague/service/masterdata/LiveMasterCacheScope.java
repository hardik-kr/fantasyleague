package com.cricket.fantasyleague.service.masterdata;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.entity.table.Match;

/**
 * Holds the live match context used by {@link MasterDataReadService#refreshIfLiveMatchActive()}
 * so the 5-minute tick does not call {@code findActiveLiveMatch} or full catalog reloads.
 * Single-replica oriented; multi-replica setups may duplicate refresh work unless scope is shared externally.
 */
@Component
public class LiveMasterCacheScope {

    private static final Logger logger = LoggerFactory.getLogger(LiveMasterCacheScope.class);

    private final AtomicReference<LiveMasterSnapshot> ref = new AtomicReference<>();

    public LiveMasterSnapshot get() {
        return ref.get();
    }

    /**
     * Publishes or replaces the snapshot from the live pipeline (player ids are those in the current points map).
     */
    public void updateFromPlayerPointsMap(Match match, Map<Integer, Double> playerPointsByPlayerId) {
        if (match == null || match.getId() == null || match.getLeagueId() == null) {
            return;
        }
        if (playerPointsByPlayerId == null || playerPointsByPlayerId.isEmpty()) {
            return;
        }
        int[] ids = playerPointsByPlayerId.keySet().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .distinct()
                .sorted()
                .toArray();
        if (ids.length == 0) {
            return;
        }
        LiveMasterSnapshot snap = new LiveMasterSnapshot(match.getId(), match.getLeagueId(), Arrays.copyOf(ids, ids.length), Instant.now());
        ref.set(snap);
        logger.debug("Live master cache scope: matchId={} leagueId={} playerCount={}", snap.matchId(), snap.leagueId(), ids.length);
    }

    /**
     * Clears scope when the given match finished finalization (avoid wiping a newer match if ids reused).
     */
    public void clearIfMatch(int matchId) {
        ref.updateAndGet(s -> (s != null && s.matchId() == matchId) ? null : s);
    }

    /** Clears any published live scope (e.g. admin full evict). */
    public void clear() {
        ref.set(null);
    }
}
