package com.cricket.fantasyleague.service.daily;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized Micrometer instruments for the Daily Challenge flow.
 *
 * <p>Single component (DRY) so panel/alert names stay aligned across services.
 * All metrics are tagged with {@code mode=daily} to avoid accidental collisions
 * with season-long metrics in dashboards.
 */
@Component
public class DailyMetrics {

    private static final String TAG_MODE = "mode";
    private static final String VALUE_MODE = "daily";

    private final Counter draftUpserted;
    private final Counter teamLocked;
    private final Counter teamLockSkipped;
    private final Counter pointsRecomputeBatches;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Timer matchLockTimer;
    private final Timer pointsRecomputeTimer;
    private final Timer cacheWarmupTimer;
    private final Timer cacheFlushTimer;

    public DailyMetrics(MeterRegistry registry) {
        this.draftUpserted = Counter.builder("fantasy.daily.draft.upserts")
                .description("Daily Challenge draft upserts (creates + updates)")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.teamLocked = Counter.builder("fantasy.daily.teams.locked")
                .description("Daily Challenge teams promoted from draft to locked")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.teamLockSkipped = Counter.builder("fantasy.daily.teams.lock_skipped")
                .description("Daily Challenge drafts skipped during lock (already promoted)")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.pointsRecomputeBatches = Counter.builder("fantasy.daily.points.batches")
                .description("Daily Challenge match-points recompute batches flushed")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.cacheHits = Counter.builder("fantasy.daily.cache.hits")
                .description("Daily Challenge hot-path reads served from DailyLiveMatchTeamCache")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.cacheMisses = Counter.builder("fantasy.daily.cache.misses")
                .description("Daily Challenge hot-path reads that fell through to the database")
                .tag(TAG_MODE, VALUE_MODE)
                .register(registry);
        this.matchLockTimer = Timer.builder("fantasy.daily.lock.duration")
                .description("End-to-end duration of a Daily Challenge match lock")
                .tag(TAG_MODE, VALUE_MODE)
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
        this.pointsRecomputeTimer = Timer.builder("fantasy.daily.points.duration")
                .description("End-to-end duration of a Daily Challenge points recompute pass")
                .tag(TAG_MODE, VALUE_MODE)
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
        this.cacheWarmupTimer = Timer.builder("fantasy.daily.cache.warmup.duration")
                .description("DailyLiveMatchTeamCache warm-up duration (load all locked teams for one match)")
                .tag(TAG_MODE, VALUE_MODE)
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
        this.cacheFlushTimer = Timer.builder("fantasy.daily.cache.flush.duration")
                .description("DailyLiveMatchTeamCache periodic flush duration (cache -> daily_user_match_team)")
                .tag(TAG_MODE, VALUE_MODE)
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }

    public void onDraftUpsert() { draftUpserted.increment(); }
    public void onTeamsLocked(long count) { if (count > 0) teamLocked.increment(count); }
    public void onTeamsLockSkipped(long count) { if (count > 0) teamLockSkipped.increment(count); }
    public void onPointsBatchFlushed() { pointsRecomputeBatches.increment(); }
    public void onCacheHit() { cacheHits.increment(); }
    public void onCacheMiss() { cacheMisses.increment(); }
    public Timer matchLockTimer() { return matchLockTimer; }
    public Timer pointsRecomputeTimer() { return pointsRecomputeTimer; }
    public Timer cacheWarmupTimer() { return cacheWarmupTimer; }
    public Timer cacheFlushTimer() { return cacheFlushTimer; }
}
