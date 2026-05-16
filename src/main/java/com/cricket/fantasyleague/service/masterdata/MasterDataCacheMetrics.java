package com.cricket.fantasyleague.service.masterdata;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer counters for master match/player cache (no-op when Actuator registry absent).
 */
@Component
public class MasterDataCacheMetrics {

    private final Counter matchHits;
    private final Counter matchMisses;
    private final Counter playerHits;
    private final Counter playerMisses;

    public MasterDataCacheMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            this.matchHits = Counter.builder("fantasy.master_cache.match.hits").register(registry);
            this.matchMisses = Counter.builder("fantasy.master_cache.match.misses").register(registry);
            this.playerHits = Counter.builder("fantasy.master_cache.player.hits").register(registry);
            this.playerMisses = Counter.builder("fantasy.master_cache.player.misses").register(registry);
        } else {
            this.matchHits = null;
            this.matchMisses = null;
            this.playerHits = null;
            this.playerMisses = null;
        }
    }

    public void onMatchHit() {
        if (matchHits != null) {
            matchHits.increment();
        }
    }

    public void onMatchMiss() {
        if (matchMisses != null) {
            matchMisses.increment();
        }
    }

    public void onPlayerHit() {
        if (playerHits != null) {
            playerHits.increment();
        }
    }

    public void onPlayerMiss() {
        if (playerMisses != null) {
            playerMisses.increment();
        }
    }
}
