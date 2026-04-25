package com.cricket.fantasyleague.service.workflow;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDateTime;
import static com.cricket.fantasyleague.util.MatchTimeUtils.toIST;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.cache.store.CacheStore;
import com.cricket.fantasyleague.cache.store.CacheStoreFactory;
import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.playerpoints.LiveMatchPlayerPointsPersistServiceImpl;
import com.cricket.fantasyleague.service.playerpoints.LiveMatchPlayerPointsService;
import com.cricket.fantasyleague.service.usermatchstats.UserMatchStatsService;
import com.cricket.fantasyleague.service.useroverallpts.UserOverallPtsService;
import com.cricket.fantasyleague.service.usertransfer.UserTransferService;

/**
 * Orchestrates the full live-match pipeline.
 *
 * Pipeline per match (all in-memory, zero DB in the hot loop):
 *   1. Job1: Player points (cached scorecard, 1 HTTP call per 25s window)
 *   2. Job2: User match points — runs async after Job1 completes
 *   3. Job3: User overall points — chains after Job2 (needs matchPointsByUser)
 *
 * Job2 + Job3 execute off the main scheduler thread via CompletableFuture.
 * DB writes happen only via periodic flushToDB() calls, not on every ball.
 *
 * <p><b>Cache scoping invariant:</b> player-config caches are <i>strictly
 * per-match</i>. The pipeline of one match never touches the cache of another.
 * Each match's baseline is computed exactly once on first cycle and reused
 * for the lifetime of that match (until {@link #finalFlushAndEvict}). This
 * prevents cross-match cache thrashing that would otherwise corrupt totals
 * for players active during overlapping match windows.
 */
@Service
public class LiveMatchWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchWorkflowService.class);

    private final LiveMatchCache liveMatchCache;
    private final LiveMatchUserCache liveMatchUserCache;
    private final LiveMatchPlayerPointsService playerPointsService;
    private final LiveMatchPlayerPointsPersistServiceImpl playerPointsPersist;
    private final UserMatchStatsService userMatchStatsService;
    private final UserOverallPtsService userOverallPtsService;
    private final UserTransferService userTransferService;
    private final MatchService matchService;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final PlayerPointsRepository playerPointsRepository;
    private final CacheStoreFactory cacheStoreFactory;
    private final Executor taskExecutor;

    @Value("${fantasy.cache.strategy:1}")
    private int strategy;

    // ── Strategy 1: in-memory ConcurrentHashMaps (current behavior) ──
    // Map<MatchId, Map<PlayerId, FantasyPlayerConfig>> — strictly match-scoped.
    private final ConcurrentHashMap<Integer, Map<Integer, FantasyPlayerConfig>> inMemPlayerConfig = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Map<Integer, Double>> inMemPlayerBaseline = new ConcurrentHashMap<>();

    // ── Strategy 2: Redis-backed CacheStores ──
    // Already keyed Map<MatchId, CacheStore<PlayerId, …>> — same isolation guarantee.
    private final ConcurrentHashMap<Integer, CacheStore<Integer, FantasyPlayerConfig>> redisConfigStores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CacheStore<Integer, Double>> redisBaselineStores = new ConcurrentHashMap<>();

    // Per-match dirty tracking. A match enters this set when a tick mutates
    // its in-cache totalPoints; it exits when those mutations are flushed to
    // DB. Replaces the previous global volatile boolean which dropped writes
    // when 2+ matches were live simultaneously.
    private final Set<Integer> dirtyConfigMatches = ConcurrentHashMap.newKeySet();

    /**
     * Serializes the pipeline tick (in-memory mutations) with the periodic flush
     * (cache → DB writes). Prevents torn reads/writes where a flush observes a
     * partially updated in-memory state mid-tick, which could cause a subsequent
     * warmUp on restart to pick up an inconsistent committedTotal snapshot.
     * Both paths block each other for a short window; they run on different
     * scheduler threads so this just orders them.
     */
    private final ReentrantLock pipelineFlushLock = new ReentrantLock();

    public LiveMatchWorkflowService(LiveMatchCache liveMatchCache,
                                    LiveMatchUserCache liveMatchUserCache,
                                    LiveMatchPlayerPointsService playerPointsService,
                                    LiveMatchPlayerPointsPersistServiceImpl playerPointsPersist,
                                    UserMatchStatsService userMatchStatsService,
                                    UserOverallPtsService userOverallPtsService,
                                    UserTransferService userTransferService,
                                    MatchService matchService,
                                    FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                                    PlayerPointsRepository playerPointsRepository,
                                    CacheStoreFactory cacheStoreFactory,
                                    @Qualifier("fantasyTaskExecutor") Executor taskExecutor) {
        this.liveMatchCache = liveMatchCache;
        this.liveMatchUserCache = liveMatchUserCache;
        this.playerPointsService = playerPointsService;
        this.playerPointsPersist = playerPointsPersist;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.userTransferService = userTransferService;
        this.matchService = matchService;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.playerPointsRepository = playerPointsRepository;
        this.cacheStoreFactory = cacheStoreFactory;
        this.taskExecutor = taskExecutor;
    }

    public void processMatchPipeline(Match match) {
        logger.info("Pipeline START for matchId={} at {}", match.getId(), nowDateTime());

        pipelineFlushLock.lock();
        try {
            liveMatchUserCache.warmUp(match);
            ensurePlayerConfigInitialized(match);

            Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);

            CompletableFuture<Void> pipeline = CompletableFuture
                    .runAsync(() -> userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap), taskExecutor)
                    .thenRunAsync(() -> userOverallPtsService.calcUserOverallPointsData(match), taskExecutor);

            pipeline.join();

            updatePlayerTotalPointsLive(match, playerPointsMap);

            logger.info("Pipeline END for matchId={} at {}", match.getId(), nowDateTime());

            if (Boolean.TRUE.equals(match.getIsMatchComplete())) {
                Match freshMatch = matchService.findMatchById(match.getId());
                if (freshMatch != null && freshMatch.getMatchState() == MatchState.COMPLETE) {
                    finalFlushAndEvict(match);
                } else {
                    logger.warn("matchId={} has isMatchComplete=true but matchState={} — skipping finalization",
                            match.getId(), freshMatch != null ? freshMatch.getMatchState() : "null");
                }
            }
        } finally {
            pipelineFlushLock.unlock();
        }
    }

    private void finalFlushAndEvict(Match match) {
        List<PlayerPoints> records = liveMatchCache.getPlayerPointsRecords(match.getId());
        if (records != null && !records.isEmpty()) {
            playerPointsPersist.saveAllPlayerPoints(records);
        }
        flushPlayerConfigToDB(match.getId());
        // No promotePrevPoints: totalpoints is already correct from the last tick
        // (totalpoints = committedTotal + SUM(live matchpoints)). The next match's
        // warmUp re-derives committedTotal fresh from DB so no "promotion" is needed.
        liveMatchUserCache.flushToDB();
        liveMatchUserCache.evictMatch(match.getId());
        liveMatchCache.evictMatch(match.getId());
        evictPlayerConfigCache(match.getId());
        logger.info("matchId={} complete — final flush done, cache evicted", match.getId());
    }

    /**
     * Lazily initialises the player-config cache for {@code match} on its
     * first pipeline tick. Subsequent ticks reuse the cache untouched: the
     * baseline computed here is the only baseline ever used for this match,
     * which guarantees idempotency across ticks (each tick simply rewrites
     * {@code totalPoints = baseline + currentMatchPoints}).
     *
     * <p>Crucially this method <b>never touches another match's cache</b>.
     * A previously-live match retains its cache until its own
     * {@link #finalFlushAndEvict} runs (or the defensive sweep in
     * {@link #flushCacheToDB} catches a missed completion).
     */
    private void ensurePlayerConfigInitialized(Match match) {
        Integer leagueId = match.getLeagueId();
        if (leagueId == null) return;
        if (hasPlayerConfig(match.getId())) return;
        initPlayerConfigCache(match.getId(), leagueId);
    }

    private boolean hasPlayerConfig(Integer matchId) {
        if (strategy == 1) {
            return inMemPlayerConfig.containsKey(matchId);
        }
        return redisConfigStores.containsKey(matchId);
    }

    private void updatePlayerTotalPointsLive(Match match, Map<Integer, Double> playerPointsMap) {
        Integer leagueId = match.getLeagueId();
        if (leagueId == null || playerPointsMap.isEmpty()) return;

        Map<Integer, FantasyPlayerConfig> configMap;
        Map<Integer, Double> baselines;

        if (strategy == 1) {
            configMap = inMemPlayerConfig.get(match.getId());
            baselines = inMemPlayerBaseline.get(match.getId());
        } else {
            CacheStore<Integer, FantasyPlayerConfig> cfgStore = redisConfigStores.get(match.getId());
            CacheStore<Integer, Double> blStore = redisBaselineStores.get(match.getId());
            if (cfgStore == null || blStore == null) return;
            configMap = new HashMap<>(cfgStore.asMap());
            baselines = new HashMap<>(blStore.asMap());
        }

        if (configMap == null || baselines == null) return;

        // Batch-resolve any playerIds not yet cached. This handles the very
        // first cycle of a match (cache is empty because no PlayerPoints
        // existed in DB at init time) AND true mid-match impact subs in a
        // single DB call instead of N+1 round-trips.
        List<Integer> missing = new ArrayList<>();
        for (Integer playerId : playerPointsMap.keySet()) {
            if (!configMap.containsKey(playerId)) missing.add(playerId);
        }

        if (!missing.isEmpty()) {
            boolean isFirstCyclePreload = configMap.isEmpty();
            List<FantasyPlayerConfig> fetched =
                    fantasyPlayerConfigRepository.findByLeagueIdAndPlayerIdIn(leagueId, missing);

            for (FantasyPlayerConfig cfg : fetched) {
                Integer pid = cfg.getPlayerId();
                if (pid == null) continue;
                configMap.put(pid, cfg);
                baselines.put(pid, cfg.getTotalPoints() != null ? cfg.getTotalPoints() : 0.0);
            }

            if (isFirstCyclePreload) {
                logger.info("matchId={} first cycle: pre-loaded {} playing-XI player configs",
                        match.getId(), fetched.size());
            } else {
                logger.info("matchId={} late-discovered {} player config(s) (impact sub / concussion sub)",
                        match.getId(), fetched.size());
            }
        }

        int updated = 0;
        for (Map.Entry<Integer, Double> entry : playerPointsMap.entrySet()) {
            Integer playerId = entry.getKey();
            FantasyPlayerConfig cfg = configMap.get(playerId);
            if (cfg == null) continue; // unresolved — config row missing entirely

            double baseline = baselines.getOrDefault(playerId, 0.0);
            cfg.setTotalPoints(baseline + entry.getValue());
            updated++;
        }

        if (updated > 0) {
            dirtyConfigMatches.add(match.getId());
            if (strategy == 2) {
                CacheStore<Integer, FantasyPlayerConfig> cfgStore = redisConfigStores.get(match.getId());
                CacheStore<Integer, Double> blStore = redisBaselineStores.get(match.getId());
                if (cfgStore != null) cfgStore.putAll(configMap);
                if (blStore != null) blStore.putAll(baselines);
            }
        }
    }

    private void initPlayerConfigCache(Integer matchId, Integer leagueId) {
        // Read what's already been persisted to DB.PlayerPoints for this match.
        // This is the value that has already been folded into cfg.totalPoints
        // by a prior flushPlayerConfigToDB or finalFlushAndEvict — i.e. the
        // amount we must subtract from the current cfg.totalPoints to recover
        // the pre-match baseline.
        //
        // On the very first cycle of a fresh match, this returns empty and
        // baseline = cfg.totalPoints (full DB value).
        //
        // On a restart-during-match, both cfg.totalPoints and PlayerPoints
        // were last written by the same flushCacheToDB call (so they are
        // mutually consistent), and ApplicationReadyEvent's integrity check
        // (syncTotalPointsFromPlayerPoints) further guarantees this on boot.
        Map<Integer, Double> alreadyFlushedMatchPoints = new HashMap<>();
        List<Integer> matchPlayerIds = new ArrayList<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            if (pp.getPlayerId() != null) {
                alreadyFlushedMatchPoints.put(pp.getPlayerId(), pp.getPlayerpoints());
                matchPlayerIds.add(pp.getPlayerId());
            }
        }

        List<FantasyPlayerConfig> configs = matchPlayerIds.isEmpty()
                ? Collections.emptyList()
                : fantasyPlayerConfigRepository.findByLeagueIdAndPlayerIdIn(leagueId, matchPlayerIds);

        Map<Integer, FantasyPlayerConfig> map = new HashMap<>(configs.size());
        Map<Integer, Double> baselines = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            map.put(cfg.getPlayerId(), cfg);
            double dbTotal = cfg.getTotalPoints() != null ? cfg.getTotalPoints() : 0.0;
            double matchPts = alreadyFlushedMatchPoints.getOrDefault(cfg.getPlayerId(), 0.0);
            baselines.put(cfg.getPlayerId(), dbTotal - matchPts);
        }

        if (strategy == 1) {
            inMemPlayerConfig.put(matchId, map);
            inMemPlayerBaseline.put(matchId, baselines);
        } else {
            CacheStore<Integer, FantasyPlayerConfig> cfgStore =
                    cacheStoreFactory.create("playerConfig:" + matchId, Integer.class, FantasyPlayerConfig.class);
            CacheStore<Integer, Double> blStore =
                    cacheStoreFactory.create("playerBaseline:" + matchId, Integer.class, Double.class);
            cfgStore.putAll(map);
            blStore.putAll(baselines);
            redisConfigStores.put(matchId, cfgStore);
            redisBaselineStores.put(matchId, blStore);
        }

        logger.info("Initialized player-config cache for matchId={}: {} pre-existing player(s) (subtracted {} prior-flush match points)",
                matchId, configs.size(), alreadyFlushedMatchPoints.size());
    }

    private void flushPlayerConfigToDB(Integer matchId) {
        if (strategy == 1) {
            Map<Integer, FantasyPlayerConfig> configMap = inMemPlayerConfig.get(matchId);
            if (configMap != null && !configMap.isEmpty()) {
                fantasyPlayerConfigRepository.saveAll(configMap.values());
                dirtyConfigMatches.remove(matchId);
                logger.info("Flushed player totalPoints for matchId={}, {} players", matchId, configMap.size());
            }
        } else {
            CacheStore<Integer, FantasyPlayerConfig> store = redisConfigStores.get(matchId);
            if (store != null && store.size() > 0) {
                fantasyPlayerConfigRepository.saveAll(store.values());
                dirtyConfigMatches.remove(matchId);
                logger.info("Flushed player totalPoints for matchId={}, {} players", matchId, store.size());
            }
        }
    }

    private void evictPlayerConfigCache(Integer matchId) {
        if (strategy == 1) {
            inMemPlayerConfig.remove(matchId);
            inMemPlayerBaseline.remove(matchId);
        } else {
            CacheStore<Integer, FantasyPlayerConfig> cfgStore = redisConfigStores.remove(matchId);
            CacheStore<Integer, Double> blStore = redisBaselineStores.remove(matchId);
            if (cfgStore != null) cfgStore.clear();
            if (blStore != null) blStore.clear();
        }
        dirtyConfigMatches.remove(matchId);
    }

    /**
     * Defensive cleanup for player-config caches that outlived their match —
     * e.g. if {@link #finalFlushAndEvict} was missed because the match
     * transitioned to COMPLETE outside the pipeline window. Without this,
     * a stale entry would otherwise live forever in {@code inMemPlayerConfig}
     * and slowly leak heap.
     *
     * <p>Runs under the pipelineFlushLock from {@link #flushCacheToDB} so it
     * cannot race with an in-flight tick.
     */
    private void evictNonLivePlayerConfigCaches(Set<Integer> liveMatchIds) {
        Set<Integer> cachedMatchIds = strategy == 1
                ? new HashSet<>(inMemPlayerConfig.keySet())
                : new HashSet<>(redisConfigStores.keySet());

        for (Integer cachedMatchId : cachedMatchIds) {
            if (liveMatchIds.contains(cachedMatchId)) continue;
            // Match is no longer live but its cache is still around: flush
            // pending mutations first (so they don't get lost), then evict.
            logger.warn("Defensive sweep: evicting orphaned player-config cache for matchId={} (no longer live)",
                    cachedMatchId);
            if (dirtyConfigMatches.contains(cachedMatchId)) {
                flushPlayerConfigToDB(cachedMatchId);
            }
            evictPlayerConfigCache(cachedMatchId);
        }
    }

    public void flushCacheToDB() {
        // Serialize against the pipeline tick to avoid torn writes: flushing
        // mid-tick could persist a half-updated cache state. Both are scheduled
        // tasks on different threads, so we just order them.
        pipelineFlushLock.lock();
        try {
            if (liveMatchUserCache.hasDirtyData()) {
                liveMatchUserCache.flushToDB();
            }

            List<Match> liveMatches = getLiveMatches();
            Set<Integer> liveMatchIds = liveMatches.stream()
                    .map(Match::getId)
                    .collect(Collectors.toCollection(HashSet::new));

            for (Match match : liveMatches) {
                if (liveMatchCache.isDirtyPlayerPoints(match.getId())) {
                    List<PlayerPoints> records = liveMatchCache.getPlayerPointsRecords(match.getId());
                    if (records != null && !records.isEmpty()) {
                        playerPointsPersist.saveAllPlayerPoints(records);
                        liveMatchCache.clearPlayerPointsDirty(match.getId());
                        logger.debug("Flushed {} player points records for matchId={}", records.size(), match.getId());
                    }
                }
                if (dirtyConfigMatches.contains(match.getId())) {
                    flushPlayerConfigToDB(match.getId());
                }
            }

            // Safety net: clean up any cached matches that are no longer live
            // (e.g. completion path missed eviction).
            evictNonLivePlayerConfigCaches(liveMatchIds);
        } finally {
            pipelineFlushLock.unlock();
        }
    }

    public void lockTeamsForMatch(Match match) {
        logger.info("Locking teams for matchId={} at {}", match.getId(), nowDateTime());
        userTransferService.lockMatchTeam(match);
        logger.info("Teams locked for matchId={}", match.getId());
    }

    private List<Match> getLiveMatches() {
        List<Match> todayMatches = liveMatchCache.getTodayMatches();
        LocalDateTime now = nowDateTime();

        List<Match> live = new ArrayList<>();
        for (Match match : todayMatches) {
            if (match.getDate() != null && match.getTime() != null) {
                LocalDateTime matchStart = toIST(match.getDate(), match.getTime(), match.getTimezone());
                if (now.isAfter(matchStart)) {
                    live.add(match);
                }
            }
        }
        return live;
    }
}
