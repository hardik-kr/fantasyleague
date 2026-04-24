package com.cricket.fantasyleague.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.cache.dto.CachedUserMatchStats;
import com.cricket.fantasyleague.cache.dto.CachedUserOverallStats;
import com.cricket.fantasyleague.cache.store.CacheStore;
import com.cricket.fantasyleague.cache.store.CacheStoreFactory;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;

/**
 * Holds UserMatchStats and UserOverallStats for users active in currently live
 * matches. Loaded once per match via warmUp(), read with zero DB access during
 * the hot loop, and flushed periodically to the database in bulk.
 *
 * <p>Core design: the authoritative total for a user is
 * {@code committedTotalByUser + SUM(matchpoints across all live matches)}.
 * {@code committedTotalByUser} is a per-warm-up snapshot of
 * {@code SUM(user_match_stats.matchpoints) WHERE match_id NOT IN liveMatchIds}.
 * It is immutable for the life of the match; totalpoints is overwritten (not
 * accumulated) every tick. This eliminates the {@code prevpoints}-based drift
 * problem by construction.
 *
 * <p>Storage strategy is selected by {@code fantasy.cache.strategy}:
 * <ul>
 *   <li>1 (default) — in-memory ConcurrentHashMap (JPA entities held directly)</li>
 *   <li>2 — Redis via CacheStore (lightweight DTOs serialized as JSON)</li>
 * </ul>
 */
@Service
public class LiveMatchUserCache {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchUserCache.class);

    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final CacheStoreFactory cacheStoreFactory;
    private final JdbcTemplate jdbcTemplate;

    @Value("${fantasy.cache.strategy:1}")
    private int strategy;

    @Value("${fantasy.cache.flush.batch-size:10000}")
    private int flushBatchSize;

    // ── Strategy 1: in-memory JPA entities ──
    private final ConcurrentHashMap<Integer, List<UserMatchStats>> inMemMatchStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, UserOverallStats> inMemOverallStats = new ConcurrentHashMap<>();

    // ── Strategy 2: Redis-backed CacheStores with DTOs ──
    private final ConcurrentHashMap<Integer, CacheStore<Long, CachedUserMatchStats>> redisMatchStores = new ConcurrentHashMap<>();
    private CacheStore<Long, CachedUserOverallStats> redisOverallStore;

    // ── Committed-total snapshot: userId -> SUM(matchpoints) of all non-live matches ──
    // Immutable for the life of each match; re-derived fresh in warmUp().
    private final ConcurrentHashMap<Long, Double> committedTotalByUser = new ConcurrentHashMap<>();

    // ── Dirty tracking ──
    private final Set<Integer> dirtyMatchIds = ConcurrentHashMap.newKeySet();
    private volatile boolean overallDirty = false;

    public LiveMatchUserCache(UserMatchStatsRespository userMatchStatsRepository,
                              UserOverallStatsRepository userOverallStatsRepository,
                              CacheStoreFactory cacheStoreFactory,
                              JdbcTemplate jdbcTemplate) {
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.cacheStoreFactory = cacheStoreFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ────────────────────── warm-up ──────────────────────

    @Transactional(readOnly = true)
    public void warmUp(Match match) {
        if (isWarmedUp(match.getId())) return;

        List<UserMatchStats> stats = userMatchStatsRepository.findByMatchid(match);
        for (UserMatchStats s : stats) {
            Hibernate.initialize(s.getPlaying11());
        }

        Set<Long> activeUserIds = new HashSet<>(stats.size());
        for (UserMatchStats s : stats) {
            if (s.getUserid() != null) {
                activeUserIds.add(s.getUserid().getId());
            }
        }

        if (strategy == 1) {
            inMemMatchStats.put(match.getId(), stats);
        } else {
            CacheStore<Long, CachedUserMatchStats> store =
                    cacheStoreFactory.create("matchStats:" + match.getId(), Long.class, CachedUserMatchStats.class);
            Map<Long, CachedUserMatchStats> batch = new HashMap<>(stats.size());
            for (UserMatchStats s : stats) {
                if (s.getUserid() != null) {
                    batch.put(s.getUserid().getId(), CachedUserMatchStats.from(s));
                }
            }
            store.putAll(batch);
            redisMatchStores.put(match.getId(), store);
        }

        logger.info("Warmed up {} UserMatchStats for match {} (strategy={}, activeUsers={})",
                stats.size(), match.getId(), strategy, activeUserIds.size());

        loadOverallStatsForActiveUsers(activeUserIds);
        loadCommittedTotalsForActiveUsers(activeUserIds);
    }

    /**
     * Loads UserOverallStats for the supplied active users into the cache, merging
     * with any already-present entries from other concurrently-live matches.
     */
    private void loadOverallStatsForActiveUsers(Set<Long> activeUserIds) {
        if (activeUserIds.isEmpty()) return;

        List<Long> toFetch = new ArrayList<>();
        if (strategy == 1) {
            for (Long userId : activeUserIds) {
                if (!inMemOverallStats.containsKey(userId)) toFetch.add(userId);
            }
        } else {
            if (redisOverallStore == null) {
                redisOverallStore = cacheStoreFactory.create("overallStats", Long.class, CachedUserOverallStats.class);
            }
            Map<Long, CachedUserOverallStats> existing = redisOverallStore.asMap();
            for (Long userId : activeUserIds) {
                if (!existing.containsKey(userId)) toFetch.add(userId);
            }
        }

        if (toFetch.isEmpty()) {
            logger.info("Overall stats already cached for all {} active users", activeUserIds.size());
            return;
        }

        List<UserOverallStats> loaded = userOverallStatsRepository.findAllByUserIdIn(toFetch);

        if (strategy == 1) {
            for (UserOverallStats o : loaded) {
                if (o.getUserid() != null) {
                    inMemOverallStats.put(o.getUserid().getId(), o);
                }
            }
        } else {
            Map<Long, CachedUserOverallStats> batch = new HashMap<>(loaded.size());
            for (UserOverallStats o : loaded) {
                if (o.getUserid() != null) {
                    batch.put(o.getUserid().getId(), CachedUserOverallStats.from(o));
                }
            }
            redisOverallStore.putAll(batch);
        }

        logger.info("Loaded {} UserOverallStats for {} requested active users (strategy={})",
                loaded.size(), toFetch.size(), strategy);
    }

    /**
     * Snapshots SUM(matchpoints) per active user from all non-live matches into
     * {@code committedTotalByUser}. Called once per warm-up and never mutated
     * during the match lifecycle.
     */
    private void loadCommittedTotalsForActiveUsers(Set<Long> activeUserIds) {
        if (activeUserIds.isEmpty()) return;

        List<Integer> liveMatchIds = new ArrayList<>(getLiveMatchIds());
        if (liveMatchIds.isEmpty()) {
            // Shouldn't happen — warmUp just added one — but guard anyway.
            liveMatchIds.add(-1);
        }

        List<Long> userIds = new ArrayList<>(activeUserIds);
        List<Object[]> rows = userMatchStatsRepository.sumMatchPointsByUserExcludingMatches(userIds, liveMatchIds);

        int populated = 0;
        for (Object[] row : rows) {
            Long userId = ((Number) row[0]).longValue();
            double sum = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            committedTotalByUser.put(userId, sum);
            populated++;
        }
        for (Long userId : activeUserIds) {
            committedTotalByUser.putIfAbsent(userId, 0.0);
        }

        logger.info("Snapshotted committedTotal for {}/{} active users (excluded {} live matches)",
                populated, activeUserIds.size(), liveMatchIds.size());
    }

    // ────────────────────── read access ──────────────────────

    public List<UserMatchStats> getUserMatchStats(Integer matchId) {
        if (strategy == 1) {
            List<UserMatchStats> stats = inMemMatchStats.get(matchId);
            return stats != null ? stats : Collections.emptyList();
        }
        CacheStore<Long, CachedUserMatchStats> store = redisMatchStores.get(matchId);
        if (store == null) return Collections.emptyList();
        return store.values().stream().map(CachedUserMatchStats::toEntity).toList();
    }

    public Map<Long, UserOverallStats> getOverallStatsByUserId() {
        if (strategy == 1) {
            return Collections.unmodifiableMap(new HashMap<>(inMemOverallStats));
        }
        if (redisOverallStore == null) return Collections.emptyMap();
        Map<Long, CachedUserOverallStats> dtos = redisOverallStore.asMap();
        Map<Long, UserOverallStats> result = new HashMap<>(dtos.size());
        for (Map.Entry<Long, CachedUserOverallStats> e : dtos.entrySet()) {
            try {
                Long key = Long.valueOf(e.getKey().toString());
                result.put(key, e.getValue().toEntity());
            } catch (NumberFormatException ignored) {
                // Redis keys are Strings; skip any that don't parse
            }
        }
        return result;
    }

    /**
     * Returns the committed total (SUM of matchpoints across all non-live matches)
     * for the given user. Returns 0.0 for users not in the map (late-joined or never drafted).
     */
    public double getCommittedTotal(Long userId) {
        if (userId == null) return 0.0;
        Double v = committedTotalByUser.get(userId);
        return v != null ? v : 0.0;
    }

    /**
     * Sum of matchpoints for the given user across ALL currently live matches in the cache.
     * Handles the overlapping-matches edge case (two matches live in the same window).
     */
    public double getLiveMatchpointsSum(Long userId) {
        if (userId == null) return 0.0;
        double sum = 0.0;

        if (strategy == 1) {
            for (List<UserMatchStats> list : inMemMatchStats.values()) {
                for (UserMatchStats s : list) {
                    if (s.getUserid() != null && userId.equals(s.getUserid().getId())
                            && s.getMatchpoints() != null) {
                        sum += s.getMatchpoints();
                    }
                }
            }
        } else {
            for (CacheStore<Long, CachedUserMatchStats> store : redisMatchStores.values()) {
                CachedUserMatchStats dto = store.get(userId);
                if (dto != null && dto.matchpoints() != null) {
                    sum += dto.matchpoints();
                }
            }
        }
        return sum;
    }

    public Set<Integer> getLiveMatchIds() {
        if (strategy == 1) {
            return Collections.unmodifiableSet(new HashSet<>(inMemMatchStats.keySet()));
        }
        return Collections.unmodifiableSet(new HashSet<>(redisMatchStores.keySet()));
    }

    // ────────────────────── write-back (Redis only) ──────────────────────

    /**
     * Writes mutated match stats back to the cache store.
     * For strategy=1 this is a no-op (entities are held by reference).
     * For strategy=2 this persists mutations to Redis.
     */
    public void saveMatchStats(Integer matchId, List<UserMatchStats> stats) {
        if (strategy == 1) return;
        CacheStore<Long, CachedUserMatchStats> store = redisMatchStores.get(matchId);
        if (store == null) return;
        Map<Long, CachedUserMatchStats> batch = new HashMap<>(stats.size());
        for (UserMatchStats s : stats) {
            if (s.getUserid() != null) {
                batch.put(s.getUserid().getId(), CachedUserMatchStats.from(s));
            }
        }
        store.putAll(batch);
    }

    /**
     * Writes mutated overall stats back to the cache store.
     * For strategy=1 this is a no-op (entities are held by reference).
     * For strategy=2 this persists mutations to Redis.
     */
    public void saveOverallStats(Map<Long, UserOverallStats> statsMap) {
        if (strategy == 1) return;
        if (redisOverallStore == null) return;
        Map<Long, CachedUserOverallStats> batch = new HashMap<>(statsMap.size());
        for (Map.Entry<Long, UserOverallStats> e : statsMap.entrySet()) {
            batch.put(e.getKey(), CachedUserOverallStats.from(e.getValue()));
        }
        redisOverallStore.putAll(batch);
    }

    // ────────────────────── dirty tracking ──────────────────────

    public void markMatchDirty(Integer matchId) {
        dirtyMatchIds.add(matchId);
    }

    public void markOverallDirty() {
        overallDirty = true;
    }

    public boolean hasDirtyData() {
        return !dirtyMatchIds.isEmpty() || overallDirty;
    }

    // ────────────────────── flush to DB ──────────────────────

    @Transactional
    public void flushToDB() {
        if (!hasDirtyData()) return;

        long start = System.currentTimeMillis();

        if (strategy == 1) {
            flushInMemoryToDB();
        } else {
            flushRedisToDB();
        }

        logger.info("Cache flush completed in {} ms (strategy={})", System.currentTimeMillis() - start, strategy);
    }

    private void flushInMemoryToDB() {
        for (Integer matchId : dirtyMatchIds) {
            List<UserMatchStats> stats = inMemMatchStats.get(matchId);
            if (stats != null && !stats.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE user_match_stats SET matchpoints = ? WHERE id = ?",
                        stats, flushBatchSize,
                        (ps, s) -> {
                            ps.setObject(1, s.getMatchpoints());
                            ps.setLong(2, s.getId());
                        });
                logger.info("Flushed {} UserMatchStats for match {} via JDBC batch (chunk={})",
                        stats.size(), matchId, flushBatchSize);
            }
        }
        dirtyMatchIds.clear();

        if (overallDirty) {
            List<UserOverallStats> allOverall = new ArrayList<>(inMemOverallStats.values());
            if (!allOverall.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE user_overall_stats SET totalpoints = ?, " +
                                "boosterleft = ?, transferleft = ?, used_boosters = ? WHERE id = ?",
                        allOverall, flushBatchSize,
                        (ps, o) -> {
                            ps.setObject(1, o.getTotalpoints());
                            ps.setObject(2, o.getBoosterleft());
                            ps.setObject(3, o.getTransferleft());
                            ps.setString(4, o.getUsedBoosters());
                            ps.setLong(5, o.getId());
                        });
                logger.info("Flushed {} UserOverallStats via JDBC batch (chunk={})",
                        allOverall.size(), flushBatchSize);
            }
            overallDirty = false;
        }
    }

    private void flushRedisToDB() {
        for (Integer matchId : dirtyMatchIds) {
            CacheStore<Long, CachedUserMatchStats> store = redisMatchStores.get(matchId);
            if (store != null && store.size() > 0) {
                List<CachedUserMatchStats> dtos = new ArrayList<>(store.values());
                jdbcTemplate.batchUpdate(
                        "UPDATE user_match_stats SET matchpoints = ? WHERE id = ?",
                        dtos, flushBatchSize,
                        (ps, dto) -> {
                            ps.setObject(1, dto.matchpoints());
                            ps.setLong(2, dto.id());
                        });
                logger.info("Flushed {} UserMatchStats for match {} via JDBC batch (chunk={})",
                        dtos.size(), matchId, flushBatchSize);
            }
        }
        dirtyMatchIds.clear();

        if (overallDirty && redisOverallStore != null) {
            List<CachedUserOverallStats> dtos = new ArrayList<>(redisOverallStore.values());
            if (!dtos.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE user_overall_stats SET totalpoints = ?, " +
                                "boosterleft = ?, transferleft = ?, used_boosters = ? WHERE id = ?",
                        dtos, flushBatchSize,
                        (ps, dto) -> {
                            ps.setObject(1, dto.totalpoints());
                            ps.setObject(2, dto.boosterleft());
                            ps.setObject(3, dto.transferleft());
                            ps.setString(4, dto.usedBoosters());
                            ps.setLong(5, dto.id());
                        });
                logger.info("Flushed {} UserOverallStats via JDBC batch (chunk={})",
                        dtos.size(), flushBatchSize);
            }
            overallDirty = false;
        }
    }

    // ────────────────────── lifecycle ──────────────────────

    public boolean isWarmedUp(Integer matchId) {
        if (strategy == 1) {
            return inMemMatchStats.containsKey(matchId);
        }
        return redisMatchStores.containsKey(matchId);
    }

    public void evictMatch(Integer matchId) {
        if (strategy == 1) {
            inMemMatchStats.remove(matchId);
        } else {
            CacheStore<Long, CachedUserMatchStats> store = redisMatchStores.remove(matchId);
            if (store != null) store.clear();
        }
        dirtyMatchIds.remove(matchId);

        // If no more live matches, drop the snapshot + overall caches.
        if (getLiveMatchIds().isEmpty()) {
            committedTotalByUser.clear();
            if (strategy == 1) {
                inMemOverallStats.clear();
            } else if (redisOverallStore != null) {
                redisOverallStore.clear();
            }
            overallDirty = false;
        }
    }

    public void evictAll() {
        if (strategy == 1) {
            inMemMatchStats.clear();
            inMemOverallStats.clear();
        } else {
            for (CacheStore<Long, CachedUserMatchStats> store : redisMatchStores.values()) {
                store.clear();
            }
            redisMatchStores.clear();
            if (redisOverallStore != null) redisOverallStore.clear();
        }
        dirtyMatchIds.clear();
        overallDirty = false;
        committedTotalByUser.clear();
    }

    // ────────────────────── diagnostics ──────────────────────

    public Map<Integer, Integer> getAllMatchStatCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        if (strategy == 1) {
            for (var entry : inMemMatchStats.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().size());
            }
        } else {
            for (var entry : redisMatchStores.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().size());
            }
        }
        return counts;
    }

    public int getOverallStatsCount() {
        if (strategy == 1) {
            return inMemOverallStats.size();
        }
        return redisOverallStore != null ? redisOverallStore.size() : 0;
    }

    public int getCommittedTotalCount() {
        return committedTotalByUser.size();
    }
}
