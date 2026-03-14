package com.cricket.fantasyleague.service.workflow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.service.MatchService;
import com.cricket.fantasyleague.service.PlayerPointsService;
import com.cricket.fantasyleague.service.UserMatchStatsService;
import com.cricket.fantasyleague.service.UserOverallPtsService;
import com.cricket.fantasyleague.service.UserTransferService;

/**
 * Orchestrates the full live-match pipeline.
 *
 * Old design: separate controllers calling separate methods with duplicate
 *   service instances (A/B), double HTTP fetches, and sequential user processing.
 *
 * New design: single pipeline per match that flows through:
 *   1. Player points (cached scorecard → 1 HTTP call per 25s window)
 *   2. User match points (parallel across users, 0 extra DB reads for player points)
 *   3. User overall points
 *
 * Handles any number of concurrent matches dynamically (no hardcoded A/B).
 */
@Service
public class LiveMatchWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchWorkflowService.class);

    private final LiveMatchCache liveMatchCache;
    private final MatchService matchService;
    private final PlayerPointsService playerPointsService;
    private final UserMatchStatsService userMatchStatsService;
    private final UserOverallPtsService userOverallPtsService;
    private final UserTransferService userTransferService;

    public LiveMatchWorkflowService(LiveMatchCache liveMatchCache,
                                    MatchService matchService,
                                    PlayerPointsService playerPointsService,
                                    UserMatchStatsService userMatchStatsService,
                                    UserOverallPtsService userOverallPtsService,
                                    UserTransferService userTransferService) {
        this.liveMatchCache = liveMatchCache;
        this.matchService = matchService;
        this.playerPointsService = playerPointsService;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.userTransferService = userTransferService;
    }

    /**
     * Full pipeline entry point called by LiveMatchScheduler.
     * For each live match: player points → user match points → user overall points.
     */
    public void processFullPipeline() {
        List<Match> liveMatches = getLiveMatches();
        if (liveMatches.isEmpty()) {
            logger.debug("No live matches found");
            return;
        }

        for (Match match : liveMatches) {
            try {
                processMatchPipeline(match);
            } catch (Exception e) {
                logger.error("Pipeline failed for match {}: {}", match.getId(), e.getMessage(), e);
            }
        }
    }

    private void processMatchPipeline(Match match) {
        logger.info("Pipeline START for match {} at {}", match.getId(), LocalDateTime.now());

        Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);
        userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap);
        userOverallPtsService.calcUserOverallPointsData(match);

        logger.info("Pipeline END for match {} at {}", match.getId(), LocalDateTime.now());
    }

    public void processLivePlayerPoints() {
        for (Match match : getLiveMatches()) {
            playerPointsService.calculatePlayerPoints(match);
        }
    }

    public void processLiveUserMatchPoints() {
        for (Match match : getLiveMatches()) {
            Map<Integer, Double> pts = playerPointsService.calculatePlayerPoints(match);
            userMatchStatsService.calcMatchUserPointsData(match, pts);
        }
    }

    public void processLiveUserOverallPoints() {
        for (Match match : getLiveMatches()) {
            userOverallPtsService.calcUserOverallPointsData(match);
        }
    }

    public void lockTeamsForCurrentWindow() {
        LocalDate currDate = LocalDate.now();
        LocalTime currTime = LocalTime.now();

        Match currMatch = matchService.findLockedMatch(currDate, currTime);
        if (currMatch == null) return;

        LocalTime matchTime = currMatch.getTime();
        LocalTime windowEnd = matchTime.plusMinutes(10);

        if (currDate.equals(currMatch.getDate())
                && currTime.isAfter(matchTime)
                && currTime.isBefore(windowEnd)) {
            logger.info("Locking teams for match {} at {}", currMatch.getId(), LocalDateTime.now());
            userTransferService.lockMatchTeam(currMatch);
        }
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
