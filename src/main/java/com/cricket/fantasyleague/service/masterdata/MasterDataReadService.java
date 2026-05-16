package com.cricket.fantasyleague.service.masterdata;

import java.util.List;
import java.util.Optional;

import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;

/**
 * Read façade for master match list and per-league player catalog (same shapes as
 * {@code FantasyMatchService} / {@code FantasyPlayerService}). Uses
 * {@link com.cricket.fantasyleague.cache.store.CacheStoreFactory} namespaces with
 * infinite entry TTL; explicit reload/evict and live-window refresh keep data fresh.
 */
public interface MasterDataReadService {

    List<MatchResponse> getAllMatchesWithTeams();

    Optional<MatchResponse> getMatchById(Integer matchId);

    List<PlayerResponse> getAllPlayersWithConfig(Integer leagueId);

    Optional<PlayerResponse> getPlayerWithConfig(Integer leagueId, Integer playerId);

    boolean isEnabled();

    /**
     * Reloads match list from DB into the match cache and refreshes every known league’s player store.
     */
    void reloadMatchesAndCachedPlayerLeagues();

    void evictAll();

    /** When a live pipeline has published a {@link LiveMasterSnapshot}, refreshes that match + scoped players only. */
    void refreshIfLiveMatchActive();

    /**
     * Refreshes one match in the store from DB (HSET semantics). Does not clear the match catalog flag,
     * so list APIs can keep serving from the hash without a full {@code findAllMatches} pass.
     */
    void refreshMatchInCache(Integer matchId);
}
