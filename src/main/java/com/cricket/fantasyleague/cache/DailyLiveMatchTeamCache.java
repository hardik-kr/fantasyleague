package com.cricket.fantasyleague.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.cache.dto.CachedDailyUserMatchTeam;
import com.cricket.fantasyleague.cache.store.CacheStore;
import com.cricket.fantasyleague.cache.store.CacheStoreFactory;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamRepository;
import com.cricket.fantasyleague.service.daily.DailyMetrics;

import java.util.concurrent.TimeUnit;

/**
 * Daily Challenge twin of {@link LiveMatchUserCache}.
 *
 * <p>Holds locked daily teams for currently-live matches so the per-tick
 * recompute pipeline reads/writes purely in-memory (or Redis). Periodic
 * {@link #flushDirtyToDB()} writes mutated {@code match_points} to
 * {@code daily_user_match_team} in JDBC batches; {@link #finalFlushAndEvict(Integer)}
 * runs once at match completion.
 *
 * <p><b>Heap discipline</b> (per the Daily 100K scaling plan, section 1a):
 * <ul>
 *   <li>Cache size scales linearly with the actual locked-team count {@code N},
 *       not with a hardcoded 100K ceiling. {@link #warmUp(Match)} early-exits
 *       when {@code N == 0} and pre-sizes its in-memory list to {@code N}
 *       (not 100K) when {@code N > 0}.</li>
 *   <li>{@link #forEachChunk(Integer, int, Consumer)} streams DTOs in chunks
 *       of size {@code chunkSize}. The handler's chunk must not be retained
 *       beyond the callback — implementations may reuse the underlying
 *       buffer.</li>
 *   <li>Strategy 1 stores JPA entities by reference (one per user). Strategy 2
 *       serializes lightweight DTOs into a single Redis hash and streams via
 *       {@code HSCAN}.</li>
 * </ul>
 *
 * <p><b>Workload asymmetry vs. Season Long</b>: Season has draft carry-forward
 * (almost every active user has a team for every match), so its worst case
 * == its average case. Daily has manual per-match drafts, so the typical match
 * has a much smaller {@code N} than the 100K ceiling. The skip-if-empty
 * short-circuits in this class are how the daily branch contributes
 * <i>zero</i> work to the live pipeline on matches no daily user joined.
 */
@Service
public class DailyLiveMatchTeamCache {

    private static final Logger logger = LoggerFactory.getLogger(DailyLiveMatchTeamCache.class);

    private final DailyUserMatchTeamRepository teamRepo;
    private final CacheStoreFactory cacheStoreFactory;
    private final JdbcTemplate jdbcTemplate;
    private final DailyMetrics metrics;

    @Value("${fantasy.cache.strategy:1}")
    private int strategy;

    @Value("${fantasy.cache.flush.batch-size:10000}")
    private int flushBatchSize;

    /**
     * Page size for the warm-up pre-fetch from {@code daily_user_match_team}.
     * Pages over the {@code (matchId, id)} key in fixed-size chunks so the
     * heap working-set during warm-up never exceeds {@code O(pageSize)} JPA
     * entities at a time even when {@code N} is 100K.
     */
    @Value("${fantasy.daily-challenge.warmup.page-size:10000}")
    private int warmupPageSize;

    // ── Strategy 1: in-memory JPA entities (held by reference) ──
    private final ConcurrentHashMap<Integer, List<DailyUserMatchTeam>> inMemTeams = new ConcurrentHashMap<>();

    // ── Strategy 2: Redis-backed CacheStores with DTOs ──
    private final ConcurrentHashMap<Integer, CacheStore<Long, CachedDailyUserMatchTeam>> redisStores = new ConcurrentHashMap<>();

    // ── Dirty tracking ──
    private final Set<Integer> dirtyMatchIds = ConcurrentHashMap.newKeySet();

    public DailyLiveMatchTeamCache(DailyUserMatchTeamRepository teamRepo,
                                   CacheStoreFactory cacheStoreFactory,
                                   JdbcTemplate jdbcTemplate,
                                   DailyMetrics metrics) {
        this.teamRepo = teamRepo;
        this.cacheStoreFactory = cacheStoreFactory;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
    }

    // ────────────────────── warm-up ──────────────────────

    /**
     * Idempotent: subsequent calls for the same match are no-ops.
     *
     * <p><b>Empty-match short-circuit</b>: if no daily teams are locked for
     * this match, no cache entry is allocated. Callers should subsequently
     * gate downstream work on {@link #size(Integer)} {@code > 0} so the
     * daily branch contributes zero heap and zero CPU on matches with no
     * daily participants.
     */
    @Transactional(readOnly = true)
    public void warmUp(Match match) {
        if (match == null || match.getId() == null) return;
        Integer matchId = match.getId();
        if (isWarmedUp(matchId)) return;

        long total = teamRepo.countByMatchId(matchId);
        if (total == 0) {
            logger.info("Daily cache: matchId={} has no locked daily teams — skipping warm-up", matchId);
            return;
        }

        long startNanos = System.nanoTime();
        int totalLoaded = pageThroughMatch(matchId, total);
        long elapsedNanos = System.nanoTime() - startNanos;
        metrics.cacheWarmupTimer().record(elapsedNanos, TimeUnit.NANOSECONDS);

        logger.info("Daily cache: warmed up {} locked teams for matchId={} in {} ms (strategy={}, expected={})",
                totalLoaded, matchId, elapsedNanos / 1_000_000, strategy, total);
    }

    /**
     * Two-query pagination over {@code (matchId, id)}: ids first, then a
     * single fetch with {@code JOIN FETCH playing11} per page. Avoids
     * Hibernate's {@code HHH90003004} pagination + collection-fetch warning
     * and keeps the warm-up working set bounded to {@code warmupPageSize}.
     *
     * <p>Pre-sizes the strategy-1 list to the actual {@code total} (not 100K),
     * so the typical low-engagement match doesn't pay for an oversized array.
     */
    private int pageThroughMatch(Integer matchId, long total) {
        int expected = (int) Math.min(Integer.MAX_VALUE, total);

        if (strategy == 1) {
            List<DailyUserMatchTeam> aggregate = new ArrayList<>(expected);
            long afterId = 0L;
            while (true) {
                List<Long> ids = teamRepo.findIdsByMatchIdAfter(matchId, afterId,
                        PageRequest.of(0, warmupPageSize));
                if (ids.isEmpty()) break;
                List<DailyUserMatchTeam> page = teamRepo.findAllByIdInWithPlaying11(ids);
                for (DailyUserMatchTeam t : page) {
                    Hibernate.initialize(t.getPlaying11());
                    aggregate.add(t);
                }
                afterId = ids.get(ids.size() - 1);
                if (ids.size() < warmupPageSize) break;
            }
            inMemTeams.put(matchId, aggregate);
            return aggregate.size();
        }

        CacheStore<Long, CachedDailyUserMatchTeam> store =
                cacheStoreFactory.create("dailyMatchTeam:" + matchId, Long.class, CachedDailyUserMatchTeam.class);
        int loaded = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = teamRepo.findIdsByMatchIdAfter(matchId, afterId,
                    PageRequest.of(0, warmupPageSize));
            if (ids.isEmpty()) break;
            List<DailyUserMatchTeam> page = teamRepo.findAllByIdInWithPlaying11(ids);
            Map<Long, CachedDailyUserMatchTeam> batch = new HashMap<>(page.size());
            for (DailyUserMatchTeam t : page) {
                Hibernate.initialize(t.getPlaying11());
                batch.put(t.getId(), CachedDailyUserMatchTeam.from(t));
            }
            store.putAll(batch);
            loaded += batch.size();
            afterId = ids.get(ids.size() - 1);
            if (ids.size() < warmupPageSize) break;
        }
        redisStores.put(matchId, store);
        return loaded;
    }

    // ────────────────────── lifecycle predicates ──────────────────────

    public boolean isWarmedUp(Integer matchId) {
        if (matchId == null) return false;
        if (strategy == 1) return inMemTeams.containsKey(matchId);
        return redisStores.containsKey(matchId);
    }

    /** Returns {@code 0} when not warmed up or empty — lets callers cheaply skip. */
    public int size(Integer matchId) {
        if (matchId == null) return 0;
        if (strategy == 1) {
            List<DailyUserMatchTeam> list = inMemTeams.get(matchId);
            return list != null ? list.size() : 0;
        }
        CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.get(matchId);
        return store != null ? store.size() : 0;
    }

    // ────────────────────── streaming hot-path API ──────────────────────

    /**
     * Streams cached teams in bounded chunks (DTOs only). The handler's chunk
     * is a fresh {@code ArrayList<CachedDailyUserMatchTeam>}; it must not be
     * retained beyond the callback (the cache may overwrite/clear the
     * underlying buffer on the next chunk for strategy=2).
     *
     * <p>No-op when the match isn't warmed up or has zero teams — callers do
     * not need a defensive {@link #size(Integer)} check.
     */
    public void forEachChunk(Integer matchId, int chunkSize,
                             Consumer<List<CachedDailyUserMatchTeam>> handler) {
        if (matchId == null || handler == null) return;
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }

        if (strategy == 1) {
            List<DailyUserMatchTeam> all = inMemTeams.get(matchId);
            if (all == null || all.isEmpty()) return;
            List<CachedDailyUserMatchTeam> chunk = new ArrayList<>(chunkSize);
            for (DailyUserMatchTeam t : all) {
                chunk.add(CachedDailyUserMatchTeam.from(t));
                if (chunk.size() >= chunkSize) {
                    handler.accept(chunk);
                    chunk = new ArrayList<>(chunkSize);
                }
            }
            if (!chunk.isEmpty()) handler.accept(chunk);
            return;
        }

        CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.get(matchId);
        if (store == null) return;
        store.forEachChunk(chunkSize, (keys, values) -> handler.accept(new ArrayList<>(values)));
    }

    /**
     * Writes a chunk of recomputed match-points back to the cache.
     *
     * <p>Strategy 1: patches {@code matchPoints} on the cached JPA entity by
     * id-lookup (no entity rebuild). Strategy 2: a single {@code HMSET} per
     * chunk against the Redis hash.
     */
    public void saveChunk(Integer matchId, List<CachedDailyUserMatchTeam> updated) {
        if (matchId == null || updated == null || updated.isEmpty()) return;

        if (strategy == 1) {
            List<DailyUserMatchTeam> all = inMemTeams.get(matchId);
            if (all == null || all.isEmpty()) return;
            Map<Long, Double> mpById = new HashMap<>(updated.size());
            for (CachedDailyUserMatchTeam dto : updated) {
                if (dto.id() != null) mpById.put(dto.id(), dto.matchPoints());
            }
            for (DailyUserMatchTeam t : all) {
                Double mp = mpById.get(t.getId());
                if (mp != null) t.setMatchPoints(mp);
            }
            return;
        }

        CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.get(matchId);
        if (store == null) return;
        Map<Long, CachedDailyUserMatchTeam> batch = new HashMap<>(updated.size());
        for (CachedDailyUserMatchTeam dto : updated) {
            if (dto.id() != null) batch.put(dto.id(), dto);
        }
        store.putAll(batch);
    }

    // ────────────────────── read-side API for hot-path endpoints ──────────────────────

    /**
     * Returns a paged ranked snapshot of cached teams (DESC by match_points,
     * ASC by id for tiebreak) for the leaderboard hot path. Computes the
     * sort once per call from the in-cache state — no per-match memoization
     * to avoid doubling heap.
     *
     * <p>Worst case: at 100K teams a full snapshot costs ~5-8 MB of
     * short-lived DTOs which are GC-eligible immediately on return. Typical
     * leaderboard pages are size 50, so the resulting page is a tiny slice
     * out of that snapshot.
     */
    public RankedSnapshot getRankedPage(Integer matchId, int page, int size) {
        List<CachedDailyUserMatchTeam> all = collectAll(matchId);
        if (all.isEmpty()) return new RankedSnapshot(Collections.emptyList(), 0L, 0);
        all.sort(Comparator
                .<CachedDailyUserMatchTeam>comparingDouble(t -> -nullSafe(t.matchPoints()))
                .thenComparingLong(t -> t.id() != null ? t.id() : 0L));
        long totalElements = all.size();
        int totalPages = (int) Math.ceil((double) totalElements / Math.max(1, size));
        int from = Math.min(Math.max(page, 0) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new RankedSnapshot(new ArrayList<>(all.subList(from, to)), totalElements, totalPages);
    }

    /**
     * Returns the cached team for {@code (matchId, userId)} along with the
     * user's current rank — both derived from the in-cache snapshot, so this
     * costs at most one O(N) scan with no DB hit.
     */
    public CachedTeamWithRank getByUserWithRank(Integer matchId, Long userId) {
        if (matchId == null || userId == null) return null;
        List<CachedDailyUserMatchTeam> all = collectAll(matchId);
        if (all.isEmpty()) return null;
        CachedDailyUserMatchTeam mine = null;
        for (CachedDailyUserMatchTeam t : all) {
            if (userId.equals(t.userId())) {
                mine = t;
                break;
            }
        }
        if (mine == null) return null;
        double mp = nullSafe(mine.matchPoints());
        long above = 0L;
        for (CachedDailyUserMatchTeam t : all) {
            if (t == mine) continue;
            if (nullSafe(t.matchPoints()) > mp) above++;
        }
        int rank = (int) Math.min(Integer.MAX_VALUE, above + 1);
        return new CachedTeamWithRank(mine, rank);
    }

    /** Returns all cached teams for a match. Empty list when not warmed up. */
    public List<CachedDailyUserMatchTeam> collectAll(Integer matchId) {
        if (matchId == null) return Collections.emptyList();
        if (strategy == 1) {
            List<DailyUserMatchTeam> list = inMemTeams.get(matchId);
            if (list == null || list.isEmpty()) return Collections.emptyList();
            List<CachedDailyUserMatchTeam> out = new ArrayList<>(list.size());
            for (DailyUserMatchTeam t : list) {
                out.add(CachedDailyUserMatchTeam.from(t));
            }
            return out;
        }
        CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.get(matchId);
        if (store == null) return Collections.emptyList();
        Map<Long, CachedDailyUserMatchTeam> map = store.asMap();
        return new ArrayList<>(map.values());
    }

    private static double nullSafe(Double d) {
        return d != null ? d : 0.0;
    }

    // ────────────────────── dirty tracking ──────────────────────

    public void markDirty(Integer matchId) {
        if (matchId != null) dirtyMatchIds.add(matchId);
    }

    public boolean hasDirtyData() {
        return !dirtyMatchIds.isEmpty();
    }

    // ────────────────────── flush to DB ──────────────────────

    /**
     * Periodic flush. Streams cached teams in chunks and issues one JDBC
     * batchUpdate per chunk against {@code daily_user_match_team}.
     *
     * <p>Intentionally not annotated {@code @Transactional}: each chunk
     * commits independently via the JdbcTemplate's auto-commit path so a
     * 100K-row flush never holds a single long-running transaction.
     */
    public void flushDirtyToDB() {
        if (dirtyMatchIds.isEmpty()) return;

        long startNanos = System.nanoTime();
        List<Integer> snapshot = new ArrayList<>(dirtyMatchIds);
        int totalFlushed = 0;
        for (Integer matchId : snapshot) {
            totalFlushed += flushMatch(matchId);
            dirtyMatchIds.remove(matchId);
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        metrics.cacheFlushTimer().record(elapsedNanos, TimeUnit.NANOSECONDS);
        logger.info("Daily cache: flushed {} dirty match_points rows across {} matches in {} ms",
                totalFlushed, snapshot.size(), elapsedNanos / 1_000_000);
    }

    private int flushMatch(Integer matchId) {
        if (strategy == 1) {
            List<DailyUserMatchTeam> all = inMemTeams.get(matchId);
            if (all == null || all.isEmpty()) return 0;
            jdbcTemplate.batchUpdate(
                    "UPDATE daily_user_match_team SET match_points = ?, updated_at = NOW() WHERE id = ?",
                    all, flushBatchSize,
                    (ps, t) -> {
                        ps.setObject(1, t.getMatchPoints());
                        ps.setLong(2, t.getId());
                    });
            logger.info("Daily cache: flushed {} match_points rows for matchId={} (strategy=1)",
                    all.size(), matchId);
            return all.size();
        }

        CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.get(matchId);
        if (store == null || store.size() == 0) return 0;

        final int[] flushed = {0};
        store.forEachChunk(flushBatchSize, (keys, values) -> {
            jdbcTemplate.batchUpdate(
                    "UPDATE daily_user_match_team SET match_points = ?, updated_at = NOW() WHERE id = ?",
                    values, flushBatchSize,
                    (ps, dto) -> {
                        ps.setObject(1, dto.matchPoints());
                        ps.setLong(2, dto.id());
                    });
            flushed[0] += values.size();
        });
        logger.info("Daily cache: flushed {} match_points rows for matchId={} (strategy=2)",
                flushed[0], matchId);
        return flushed[0];
    }

    // ────────────────────── eviction ──────────────────────

    /**
     * Final flush + evict at match completion. Called from
     * {@code LiveMatchWorkflowService} when the match transitions to
     * {@code COMPLETE}. Returns heap to pre-match baseline.
     */
    public void finalFlushAndEvict(Integer matchId) {
        if (matchId == null) return;
        try {
            if (dirtyMatchIds.contains(matchId)) {
                flushMatch(matchId);
                dirtyMatchIds.remove(matchId);
            }
        } catch (Exception ex) {
            logger.error("Daily cache: final flush failed for matchId={} — proceeding with evict to release heap",
                    matchId, ex);
        }
        evictMatch(matchId);
    }

    public void evictMatch(Integer matchId) {
        if (matchId == null) return;
        if (strategy == 1) {
            inMemTeams.remove(matchId);
        } else {
            CacheStore<Long, CachedDailyUserMatchTeam> store = redisStores.remove(matchId);
            if (store != null) store.clear();
        }
        dirtyMatchIds.remove(matchId);
        logger.debug("Daily cache: evicted matchId={}", matchId);
    }

    public void evictAll() {
        if (strategy == 1) {
            inMemTeams.clear();
        } else {
            for (CacheStore<Long, CachedDailyUserMatchTeam> store : redisStores.values()) {
                store.clear();
            }
            redisStores.clear();
        }
        dirtyMatchIds.clear();
    }

    // ────────────────────── diagnostics ──────────────────────

    public Map<Integer, Integer> getAllMatchTeamCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        if (strategy == 1) {
            for (Map.Entry<Integer, List<DailyUserMatchTeam>> e : inMemTeams.entrySet()) {
                counts.put(e.getKey(), e.getValue().size());
            }
        } else {
            for (Map.Entry<Integer, CacheStore<Long, CachedDailyUserMatchTeam>> e : redisStores.entrySet()) {
                counts.put(e.getKey(), e.getValue().size());
            }
        }
        return counts;
    }

    // ────────────────────── value types ──────────────────────

    public record RankedSnapshot(
            List<CachedDailyUserMatchTeam> rows,
            long totalElements,
            int totalPages
    ) {
    }

    public record CachedTeamWithRank(
            CachedDailyUserMatchTeam team,
            int rank
    ) {
    }
}
