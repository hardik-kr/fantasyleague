package com.cricket.fantasyleague.service.daily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.DailyLiveMatchTeamCache;
import com.cricket.fantasyleague.cache.dto.CachedDailyUserMatchTeam;

/**
 * Recomputes Daily Challenge match_points for every locked daily team of a
 * match. Pure recompute from the live {@code playerPointsMap}: idempotent and
 * safe to call on every live tick.
 *
 * <p><b>Cache-driven hot path</b> (per the Daily 100K scaling plan, Phase 1):
 * the recompute streams cached teams in chunks via {@link DailyLiveMatchTeamCache},
 * mutates only the in-cache {@code matchPoints}, and flips the dirty flag.
 * <b>Zero DB reads or writes happen during the tick</b> — the periodic flush
 * scheduled from {@code LiveMatchWorkflowService.flushCacheToDB} (every 5 min)
 * persists dirty match_points via JDBC batchUpdate.
 *
 * <p>The {@code playerPointsMap} is the SAME reference computed once per tick
 * by {@code LiveMatchWorkflowService.processMatchPipeline} and passed into
 * both the season and daily branches — daily must NOT recompute it.
 */
@Service
public class DailyMatchPointsServiceImpl implements DailyMatchPointsService {

    private static final Logger logger = LoggerFactory.getLogger(DailyMatchPointsServiceImpl.class);

    private final DailyLiveMatchTeamCache cache;
    private final DailyMetrics metrics;
    private final int chunkSize;
    private final boolean enabled;

    public DailyMatchPointsServiceImpl(DailyLiveMatchTeamCache cache,
                                       DailyMetrics metrics,
                                       @Value("${fantasy.daily-challenge.recompute.chunk-size:1000}") int chunkSize,
                                       @Value("${fantasy.daily-challenge.enabled:false}") boolean enabled) {
        this.cache = cache;
        this.metrics = metrics;
        this.chunkSize = chunkSize;
        this.enabled = enabled;
    }

    @Override
    public void updateForLiveMatch(Integer matchId, Map<Integer, Double> playerPointsMap) {
        if (!enabled) return;
        if (matchId == null) return;
        if (playerPointsMap == null || playerPointsMap.isEmpty()) {
            logger.debug("Daily points: empty playerPointsMap for matchId={} — skip", matchId);
            return;
        }
        // Belt-and-braces: the workflow already gates on cache.size() > 0, but
        // unit tests / direct invocations (load tests, integration tests) may
        // still call here without the workflow guard. Returning early on an
        // unwarmed match is the safe default — there's nothing to recompute.
        int total = cache.size(matchId);
        if (total == 0) {
            logger.debug("Daily points: cache miss/empty for matchId={} — skip", matchId);
            return;
        }

        long startNanos = System.nanoTime();
        int[] updated = {0};
        cache.forEachChunk(matchId, chunkSize, chunk -> {
            List<CachedDailyUserMatchTeam> mutated = new ArrayList<>(chunk.size());
            for (CachedDailyUserMatchTeam t : chunk) {
                double pts = computeMatchPoints(t, playerPointsMap);
                mutated.add(t.withMatchPoints(pts));
            }
            cache.saveChunk(matchId, mutated);
            metrics.onPointsBatchFlushed();
            updated[0] += mutated.size();
        });
        if (updated[0] > 0) {
            cache.markDirty(matchId);
        }
        metrics.pointsRecomputeTimer().record(
                System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        logger.info("Daily points: matchId={} recomputed {} cached teams (cache total={})",
                matchId, updated[0], total);
    }

    /** Captain × 2, Vice-captain × 1.5, others × 1. No boosters in daily mode. */
    private double computeMatchPoints(CachedDailyUserMatchTeam team, Map<Integer, Double> playerPointsMap) {
        List<Integer> playing11 = team.playing11Ids();
        if (playing11 == null || playing11.isEmpty()) return 0.0;
        double total = 0.0;
        Integer captainId = team.captainId();
        Integer viceCaptainId = team.viceCaptainId();
        for (Integer pid : playing11) {
            if (pid == null) continue;
            Double base = playerPointsMap.get(pid);
            if (base == null) continue;
            double pts = base;
            if (captainId != null && pid.equals(captainId)) {
                pts *= 2;
            } else if (viceCaptainId != null && pid.equals(viceCaptainId)) {
                pts *= 1.5;
            }
            total += pts;
        }
        return total;
    }
}
