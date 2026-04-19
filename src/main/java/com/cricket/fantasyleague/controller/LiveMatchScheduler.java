package com.cricket.fantasyleague.controller;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDateTime;
import static com.cricket.fantasyleague.util.MatchTimeUtils.toIST;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.workflow.LiveMatchWorkflowService;

@Component
public class LiveMatchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchScheduler.class);

    private static final long LIVE_POLL_MS = 30_000;          // 30s — in-memory calc cycle
    private static final long DELAY_POLL_MS = 120_000;       // 2min — weather/toss delay
    private static final long IDLE_POLL_MS = 3_600_000;
    private static final long PRE_MATCH_BUFFER_MS = 600_000;
    private static final long FLUSH_INTERVAL_MS = 300_000;   // 5min — DB write cycle

    private volatile boolean lastPollHadDelay = false;

    private final LiveMatchWorkflowService liveMatchWorkflowService;
    private final LiveMatchCache liveMatchCache;
    private final MatchService matchService;
    private final TaskScheduler taskScheduler;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;

    @Value("${fantasy.smart.scheduler.enabled:false}")
    private boolean smartSchedulerEnabled;

    private volatile ScheduledFuture<?> nextPoll;
    private volatile ScheduledFuture<?> flushTask;
    private final Set<Integer> lockedMatchIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> finalizedMatchIds = ConcurrentHashMap.newKeySet();

    public LiveMatchScheduler(LiveMatchWorkflowService liveMatchWorkflowService,
                              LiveMatchCache liveMatchCache,
                              MatchService matchService,
                              TaskScheduler taskScheduler,
                              UserMatchStatsRespository userMatchStatsRepository,
                              UserOverallStatsRepository userOverallStatsRepository,
                              FantasyPlayerConfigRepository fantasyPlayerConfigRepository) {
        this.liveMatchWorkflowService = liveMatchWorkflowService;
        this.liveMatchCache = liveMatchCache;
        this.matchService = matchService;
        this.taskScheduler = taskScheduler;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        validateDataIntegrity();

        if (!smartSchedulerEnabled) {
            logger.info("Smart scheduler disabled via config");
            return;
        }
        logger.info("Fantasy smart scheduler started at {}", nowDateTime());
        pollLiveMatches();
        scheduleNextPoll();
        flushTask = taskScheduler.scheduleAtFixedRate(
                () -> liveMatchWorkflowService.flushCacheToDB(),
                Instant.now().plusMillis(FLUSH_INTERVAL_MS),
                Duration.ofMillis(FLUSH_INTERVAL_MS));
    }

    /**
     * Startup consistency check: compares denormalized totals with the actual
     * SUM from detail tables. Logs warnings for any discrepancy and auto-corrects
     * so the first live match starts clean.
     */
    private void validateDataIntegrity() {
        validateUserPointsIntegrity();
        validatePlayerPointsIntegrity();
    }

    private void validateUserPointsIntegrity() {
        try {
            int corrected = userOverallStatsRepository.syncTotalPointsFromMatchStats();
            if (corrected > 0) {
                logger.warn("STARTUP INTEGRITY: corrected {} user(s) with mismatched totalpoints", corrected);
            } else {
                logger.info("STARTUP INTEGRITY: all user totalpoints consistent");
            }
        } catch (Exception ex) {
            logger.error("Startup user points integrity check failed: {}", ex.getMessage(), ex);
        }
    }

    private void validatePlayerPointsIntegrity() {
        try {
            int corrected = fantasyPlayerConfigRepository.syncTotalPointsFromPlayerPoints();
            if (corrected > 0) {
                logger.warn("STARTUP INTEGRITY: corrected {} player(s) with mismatched total_points", corrected);
            } else {
                logger.info("STARTUP INTEGRITY: all player total_points consistent");
            }
        } catch (Exception ex) {
            logger.error("Startup player points integrity check failed: {}", ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (nextPoll != null) nextPoll.cancel(false);
        if (flushTask != null) flushTask.cancel(false);
        logger.info("Smart scheduler stopped");
    }

    public void pollLiveMatches() {
        logger.info("Poll cycle started at {}", nowDateTime());
        List<Match> todayMatches = liveMatchCache.getTodayMatches();

        if (todayMatches.isEmpty()) {
            logger.info("No match candidates for today");
            return;
        }

        LocalDateTime now = nowDateTime();
        int liveCount = 0;
        boolean anyDelayed = false;

        for (Match match : todayMatches) {
            if (!isStarted(match, now)) {
                continue;
            }

            if (isCompleted(match)) {
                if (finalizedMatchIds.add(match.getId())) {
                    logger.info("Finalizing completed matchId={} — forcing fresh scorecard fetch", match.getId());
                    try {
                        liveMatchCache.evictScorecard(match.getId());
                        liveMatchWorkflowService.processMatchPipeline(match);
                        logger.info("Finalization complete for matchId={}", match.getId());
                    } catch (Exception ex) {
                        logger.error("Finalization failed for matchId={}: {}", match.getId(), ex.getMessage(), ex);
                        finalizedMatchIds.remove(match.getId());
                    }
                }
                lockedMatchIds.remove(match.getId());
                continue;
            }

            if (isDelayed(match)) {
                anyDelayed = true;
                logger.info("matchId={} is DELAYED — teams remain unlocked, pipeline skipped", match.getId());
                liveCount++;
                continue;
            }

            if (lockedMatchIds.add(match.getId())) {
                if (userMatchStatsRepository.existsByMatchid(match)) {
                    logger.info("Teams already locked in DB for matchId={}, skipping", match.getId());
                } else {
                    try {
                        liveMatchWorkflowService.lockTeamsForMatch(match);
                        logger.info("Teams locked for matchId={}", match.getId());
                    } catch (Throwable ex) {
                        logger.error("Lock failed for matchId={}: {}", match.getId(), ex.getMessage(), ex);
                        lockedMatchIds.remove(match.getId());
                    }
                }
            }

            liveCount++;
            try {
                liveMatchWorkflowService.processMatchPipeline(match);
                logger.info("Pipeline complete for matchId={}", match.getId());
            } catch (Exception ex) {
                logger.error("Pipeline failed for matchId={}: {}", match.getId(), ex.getMessage(), ex);
            }
        }

        lastPollHadDelay = anyDelayed;

        if (liveCount > 0) {
            logger.info("Poll cycle complete — {} live match(es) processed{}",
                    liveCount, anyDelayed ? " (delay detected)" : "");
        }
    }

    private void scheduleNextPoll() {
        if (nextPoll != null) {
            nextPoll.cancel(false);
        }

        List<Match> todayMatches = liveMatchCache.getTodayMatches();
        long delayMs;

        if (hasLiveMatch(todayMatches)) {
            delayMs = lastPollHadDelay ? DELAY_POLL_MS : LIVE_POLL_MS;
            logger.info("{} — next poll in {}s",
                    lastPollHadDelay ? "Match delayed (weather/toss)" : "Live match detected",
                    delayMs / 1000);
        } else {
            LocalDateTime nextStart = findNextMatchStart(todayMatches);
            Match nextMatch = null;

            if (nextStart == null) {
                nextMatch = matchService.findNextUpcomingMatch();
                if (nextMatch != null) {
                    nextStart = toIST(nextMatch.getDate(), nextMatch.getTime(), nextMatch.getTimezone());
                }
            }

            if (nextStart != null) {
                long timeUntilMatch = Duration.between(nowDateTime(), nextStart).toMillis();
                String label = nextMatch != null ? "matchId=" + nextMatch.getId() : "next scheduled match";

                if (timeUntilMatch <= PRE_MATCH_BUFFER_MS) {
                    delayMs = LIVE_POLL_MS;
                    logger.info("Pre-match window — {} at {}, polling every {}s", label, nextStart, delayMs / 1000);
                } else {
                    delayMs = timeUntilMatch - PRE_MATCH_BUFFER_MS;
                    logger.info("Sleeping until pre-match window — {} at {}, next poll in {}min", label, nextStart, delayMs / 60_000);
                }
            } else {
                delayMs = IDLE_POLL_MS;
                logger.info("No upcoming matches — idle check in {}s", delayMs / 1000);
            }
        }

        nextPoll = taskScheduler.schedule(() -> {
            pollLiveMatches();
            scheduleNextPoll();
        }, Instant.now().plusMillis(delayMs));
    }

    private boolean hasLiveMatch(List<Match> matches) {
        LocalDateTime now = nowDateTime();
        for (Match match : matches) {
            if (isStarted(match, now) && !isCompleted(match)) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime findNextMatchStart(List<Match> matches) {
        LocalDateTime now = nowDateTime();
        LocalDateTime earliest = null;

        for (Match match : matches) {
            if (isCompleted(match)) continue;
            LocalDateTime start = toIST(match.getDate(), match.getTime(), match.getTimezone());
            if (start.isAfter(now) && (earliest == null || start.isBefore(earliest))) {
                earliest = start;
            }
        }
        return earliest;
    }

    private boolean isStarted(Match match, LocalDateTime now) {
        if (match.getDate() == null || match.getTime() == null) return false;
        LocalDateTime matchStart = toIST(match.getDate(), match.getTime(), match.getTimezone());
        return now.isAfter(matchStart);
    }

    private boolean isDelayed(Match match) {
        return match.getMatchState() == MatchState.DELAY;
    }

    private boolean isCompleted(Match match) {
        return Boolean.TRUE.equals(match.getIsMatchComplete());
    }
}
