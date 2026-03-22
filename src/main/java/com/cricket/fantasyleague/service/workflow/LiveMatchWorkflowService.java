package com.cricket.fantasyleague.service.workflow;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
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
    private final Executor taskExecutor;

    public LiveMatchWorkflowService(LiveMatchCache liveMatchCache,
                                    LiveMatchUserCache liveMatchUserCache,
                                    LiveMatchPlayerPointsService playerPointsService,
                                    LiveMatchPlayerPointsPersistServiceImpl playerPointsPersist,
                                    UserMatchStatsService userMatchStatsService,
                                    UserOverallPtsService userOverallPtsService,
                                    UserTransferService userTransferService,
                                    @Qualifier("fantasyTaskExecutor") Executor taskExecutor) {
        this.liveMatchCache = liveMatchCache;
        this.liveMatchUserCache = liveMatchUserCache;
        this.playerPointsService = playerPointsService;
        this.playerPointsPersist = playerPointsPersist;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.userTransferService = userTransferService;
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
        logger.info("Pipeline START for matchId={} at {}", match.getId(), LocalDateTime.now());

        liveMatchUserCache.warmUp(match);

        // Job1: calculate player points (synchronous — scorecard HTTP call)
        Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);

        // Job2 -> Job3 chain (async on fantasyTaskExecutor)
        CompletableFuture<Void> pipeline = CompletableFuture
                .supplyAsync(() -> userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap), taskExecutor)
                .thenAcceptAsync(matchPointsByUser -> userOverallPtsService.calcUserOverallPointsData(match, matchPointsByUser), taskExecutor);

        pipeline.join();

        logger.info("Pipeline END for matchId={} at {}", match.getId(), LocalDateTime.now());

        if (Boolean.TRUE.equals(match.getIsMatchComplete())) {
            finalFlushAndEvict(match);
        }
    }

    private void finalFlushAndEvict(Match match) {
        List<PlayerPoints> records = liveMatchCache.getPlayerPointsRecords(match.getId());
        if (records != null && !records.isEmpty()) {
            playerPointsPersist.saveAllPlayerPoints(records);
        }
        liveMatchUserCache.promotePrevPoints();
        liveMatchUserCache.flushToDB();
        liveMatchUserCache.evictMatch(match.getId());
        liveMatchCache.evictMatch(match.getId());
        logger.info("matchId={} complete — final flush done, cache evicted", match.getId());
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
        }
    }

    public void lockTeamsForMatch(Match match) {
        logger.info("Locking teams for matchId={} at {}", match.getId(), LocalDateTime.now());
        userTransferService.lockMatchTeam(match);
        logger.info("Teams locked for matchId={}", match.getId());
    }

    private List<Match> getLiveMatches() {
        List<Match> todayMatches = liveMatchCache.getTodayMatches();
        LocalTime now = LocalTime.now();

        List<Match> live = new ArrayList<>();
        for (Match match : todayMatches) {
            if (now.isAfter(match.getTime())) {
                live.add(match);
            }
        }
        return live;
    }
}
