package com.cricket.fantasyleague.service.workflow;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDateTime;
import static com.cricket.fantasyleague.util.MatchTimeUtils.toIST;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

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
    private final ConcurrentHashMap<Integer, Map<Integer, FantasyPlayerConfig>> inMemPlayerConfig = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Map<Integer, Double>> inMemPlayerBaseline = new ConcurrentHashMap<>();

    // ── Strategy 2: Redis-backed CacheStores ──
    private final ConcurrentHashMap<Integer, CacheStore<Integer, FantasyPlayerConfig>> redisConfigStores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CacheStore<Integer, Double>> redisBaselineStores = new ConcurrentHashMap<>();

    private volatile boolean playerConfigDirty = false;

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

    private void ensurePlayerConfigInitialized(Match match) {
        Integer leagueId = match.getLeagueId();
        if (leagueId == null) return;
        if (!hasPlayerConfig(match.getId())) {
            flushAndEvictStalePlayerConfigs();
            initPlayerConfigCache(match.getId(), leagueId);
        }
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

        int updated = 0;
        for (Map.Entry<Integer, Double> entry : playerPointsMap.entrySet()) {
            Integer playerId = entry.getKey();
            FantasyPlayerConfig cfg = configMap.get(playerId);
            if (cfg == null) {
                cfg = fantasyPlayerConfigRepository.findByPlayerIdAndLeagueId(playerId, leagueId).orElse(null);
                if (cfg == null) continue;
                configMap.put(playerId, cfg);
                baselines.put(playerId, cfg.getTotalPoints() != null ? cfg.getTotalPoints() : 0.0);
                logger.info("Late-discovered config for playerId={} (impact sub), baseline={}", playerId, baselines.get(playerId));
            }

            double baseline = baselines.getOrDefault(playerId, 0.0);
            cfg.setTotalPoints(baseline + entry.getValue());
            updated++;
        }

        if (updated > 0) {
            playerConfigDirty = true;
            if (strategy == 2) {
                CacheStore<Integer, FantasyPlayerConfig> cfgStore = redisConfigStores.get(match.getId());
                CacheStore<Integer, Double> blStore = redisBaselineStores.get(match.getId());
                if (cfgStore != null) cfgStore.putAll(configMap);
                if (blStore != null) blStore.putAll(baselines);
            }
        }
    }

    private void flushAndEvictStalePlayerConfigs() {
        if (strategy == 1) {
            for (Map.Entry<Integer, Map<Integer, FantasyPlayerConfig>> entry : inMemPlayerConfig.entrySet()) {
                fantasyPlayerConfigRepository.saveAll(entry.getValue().values());
                logger.info("Flushed stale player configs for previous matchId={}", entry.getKey());
            }
            inMemPlayerConfig.clear();
            inMemPlayerBaseline.clear();
        } else {
            for (Map.Entry<Integer, CacheStore<Integer, FantasyPlayerConfig>> entry : redisConfigStores.entrySet()) {
                fantasyPlayerConfigRepository.saveAll(entry.getValue().values());
                logger.info("Flushed stale player configs for previous matchId={}", entry.getKey());
                entry.getValue().clear();
            }
            for (CacheStore<Integer, Double> store : redisBaselineStores.values()) {
                store.clear();
            }
            redisConfigStores.clear();
            redisBaselineStores.clear();
        }
        playerConfigDirty = false;
    }

    private void initPlayerConfigCache(Integer matchId, Integer leagueId) {
        // Scope to match-relevant players only (~22-24). Late-discovered
        // impact subs are handled by the findByPlayerIdAndLeagueId fallback
        // inside updatePlayerTotalPointsLive. On the very first tick before
        // any PlayerPoints exist, this returns an empty cache and all
        // players will be discovered lazily as the scorecard arrives.
        Map<Integer, Double> alreadyFlushedMatchPoints = new HashMap<>();
        List<Integer> matchPlayerIds = new ArrayList<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            if (pp.getPlayerId() != null) {
                alreadyFlushedMatchPoints.put(pp.getPlayerId(), pp.getPlayerpoints());
                matchPlayerIds.add(pp.getPlayerId());
            }
        }

        List<FantasyPlayerConfig> configs = matchPlayerIds.isEmpty()
                ? List.of()
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

        logger.info("Snapshotted player totalPoints baseline for matchId={}, {} players (subtracted {} existing match points)",
                matchId, configs.size(), alreadyFlushedMatchPoints.size());
    }

    private void flushPlayerConfigToDB(Integer matchId) {
        if (strategy == 1) {
            Map<Integer, FantasyPlayerConfig> configMap = inMemPlayerConfig.get(matchId);
            if (configMap != null && !configMap.isEmpty()) {
                fantasyPlayerConfigRepository.saveAll(configMap.values());
                playerConfigDirty = false;
                logger.info("Flushed player totalPoints for matchId={}, {} players", matchId, configMap.size());
            }
        } else {
            CacheStore<Integer, FantasyPlayerConfig> store = redisConfigStores.get(matchId);
            if (store != null && store.size() > 0) {
                fantasyPlayerConfigRepository.saveAll(store.values());
                playerConfigDirty = false;
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
            for (Match match : getLiveMatches()) {
                if (liveMatchCache.isDirtyPlayerPoints(match.getId())) {
                    List<PlayerPoints> records = liveMatchCache.getPlayerPointsRecords(match.getId());
                    if (records != null && !records.isEmpty()) {
                        playerPointsPersist.saveAllPlayerPoints(records);
                        liveMatchCache.clearPlayerPointsDirty(match.getId());
                        logger.debug("Flushed {} player points records for matchId={}", records.size(), match.getId());
                    }
                }
                if (playerConfigDirty) {
                    flushPlayerConfigToDB(match.getId());
                }
            }
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
