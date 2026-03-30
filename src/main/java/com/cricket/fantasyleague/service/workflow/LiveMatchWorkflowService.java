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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
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
    private final Executor taskExecutor;

    private final ConcurrentHashMap<Integer, Map<Integer, FantasyPlayerConfig>> playerConfigByMatch = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Map<Integer, Double>> playerBaselineByMatch = new ConcurrentHashMap<>();
    private volatile boolean playerConfigDirty = false;

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
        this.taskExecutor = taskExecutor;
    }

    /**
     * Single-match pipeline entry point called by LiveMatchScheduler.
     *
     * Job1 (PlayerPoints) runs synchronously on the caller thread.
     * Job2 (UserMatchPoints) + Job3 (UserOverallPoints) run async
     * via CompletableFuture, chained so Job3 starts after Job2 delivers
     * its matchPointsByUser map. join() blocks until both complete.
     */
    public void processMatchPipeline(Match match) {
        logger.info("Pipeline START for matchId={} at {}", match.getId(), nowDateTime());

        liveMatchUserCache.warmUp(match);

        // Snapshot player-config baselines BEFORE player-points calculation.
        // getOrInitPlayerPoints() eagerly saves IN_PLAYING11 (4 pts) to the DB;
        // if we init baselines after that save, the baseline picks up that 4 and
        // goes negative, causing total_points to be permanently 4 less.
        ensurePlayerConfigInitialized(match);

        // Job1: calculate player points (synchronous — scorecard HTTP call)
        Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);

        // Job2 -> Job3 chain (async on fantasyTaskExecutor)
        CompletableFuture<Void> pipeline = CompletableFuture
                .supplyAsync(() -> userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap), taskExecutor)
                .thenAcceptAsync(matchPointsByUser -> userOverallPtsService.calcUserOverallPointsData(match, matchPointsByUser), taskExecutor);

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
    }

    private void finalFlushAndEvict(Match match) {
        List<PlayerPoints> records = liveMatchCache.getPlayerPointsRecords(match.getId());
        if (records != null && !records.isEmpty()) {
            playerPointsPersist.saveAllPlayerPoints(records);
        }
        flushPlayerConfigToDB(match.getId());
        liveMatchUserCache.promotePrevPoints();
        liveMatchUserCache.flushToDB();
        liveMatchUserCache.evictMatch(match.getId());
        liveMatchCache.evictMatch(match.getId());
        evictPlayerConfigCache(match.getId());
        logger.info("matchId={} complete — final flush done, cache evicted", match.getId());
    }

    /**
     * Ensures the player-config baseline cache is initialised for the given match.
     * Must be called BEFORE calculatePlayerPoints(), because that method eagerly
     * persists IN_PLAYING11 (4 pts) to the player_points table; if we snapshot
     * baselines after that write, the baseline becomes (dbTotal − 4) instead of
     * (dbTotal − 0), permanently under-counting total_points by 4.
     */
    private void ensurePlayerConfigInitialized(Match match) {
        Integer leagueId = match.getLeagueId();
        if (leagueId == null) return;
        if (!playerConfigByMatch.containsKey(match.getId())) {
            flushAndEvictStalePlayerConfigs();
            initPlayerConfigCache(match.getId(), leagueId);
        }
    }

    /**
     * On each pipeline tick, updates fantasy_player_config.total_points
     * as baseline (pre-match total) + current match points.
     */
    private void updatePlayerTotalPointsLive(Match match, Map<Integer, Double> playerPointsMap) {
        Integer leagueId = match.getLeagueId();
        if (leagueId == null || playerPointsMap.isEmpty()) return;

        Map<Integer, FantasyPlayerConfig> configMap = playerConfigByMatch.get(match.getId());
        Map<Integer, Double> baselines = playerBaselineByMatch.get(match.getId());
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
        }
    }

    /**
     * Flush and evict any cached player configs from previous matches
     * so the DB has up-to-date totalPoints before we snapshot new baselines.
     */
    private void flushAndEvictStalePlayerConfigs() {
        for (Map.Entry<Integer, Map<Integer, FantasyPlayerConfig>> entry : playerConfigByMatch.entrySet()) {
            fantasyPlayerConfigRepository.saveAll(entry.getValue().values());
            logger.info("Flushed stale player configs for previous matchId={}", entry.getKey());
        }
        playerConfigByMatch.clear();
        playerBaselineByMatch.clear();
        playerConfigDirty = false;
    }

    private void initPlayerConfigCache(Integer matchId, Integer leagueId) {
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);

        Map<Integer, Double> alreadyFlushedMatchPoints = new HashMap<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            if (pp.getPlayerId() != null) {
                alreadyFlushedMatchPoints.put(pp.getPlayerId(), pp.getPlayerpoints());
            }
        }

        Map<Integer, FantasyPlayerConfig> map = new HashMap<>(configs.size());
        Map<Integer, Double> baselines = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            map.put(cfg.getPlayerId(), cfg);
            double dbTotal = cfg.getTotalPoints() != null ? cfg.getTotalPoints() : 0.0;
            double matchPts = alreadyFlushedMatchPoints.getOrDefault(cfg.getPlayerId(), 0.0);
            baselines.put(cfg.getPlayerId(), dbTotal - matchPts);
        }
        playerConfigByMatch.put(matchId, map);
        playerBaselineByMatch.put(matchId, baselines);
        logger.info("Snapshotted player totalPoints baseline for matchId={}, {} players (subtracted {} existing match points)",
                matchId, configs.size(), alreadyFlushedMatchPoints.size());
    }

    private void flushPlayerConfigToDB(Integer matchId) {
        Map<Integer, FantasyPlayerConfig> configMap = playerConfigByMatch.get(matchId);
        if (configMap != null && !configMap.isEmpty()) {
            fantasyPlayerConfigRepository.saveAll(configMap.values());
            playerConfigDirty = false;
            logger.info("Flushed player totalPoints for matchId={}, {} players",
                    matchId, configMap.size());
        }
    }

    private void evictPlayerConfigCache(Integer matchId) {
        playerConfigByMatch.remove(matchId);
        playerBaselineByMatch.remove(matchId);
    }

    /**
     * Flushes all dirty in-memory data to the database.
     * Called by LiveMatchScheduler every 5 minutes (DB write cycle).
     */
    public void flushCacheToDB() {
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
