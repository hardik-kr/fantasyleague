package com.cricket.fantasyleague.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;

/**
 * Holds all UserMatchStats and UserOverallStats in memory during a live match.
 * Loaded once at match start via warmUp(), read with zero DB access during the
 * hot loop, and flushed periodically to the database in bulk.
 */
@Service
public class LiveMatchUserCache {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchUserCache.class);

    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;

    private final ConcurrentHashMap<Integer, List<UserMatchStats>> matchStatsByMatchId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, UserOverallStats> overallStatsByUserId = new ConcurrentHashMap<>();

    private final Set<Integer> dirtyMatchIds = ConcurrentHashMap.newKeySet();
    private volatile boolean overallDirty = false;

    public LiveMatchUserCache(UserMatchStatsRespository userMatchStatsRepository,
                              UserOverallStatsRepository userOverallStatsRepository) {
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
    }

    /**
     * Load all UserMatchStats (with playing11 eagerly initialized) and all
     * UserOverallStats into memory. Called once when a match first goes live.
     */
    @Transactional(readOnly = true)
    public void warmUp(Match match) {
        if (matchStatsByMatchId.containsKey(match.getId())) {
            return;
        }

        List<UserMatchStats> stats = userMatchStatsRepository.findByMatchid(match);
        for (UserMatchStats s : stats) {
            Hibernate.initialize(s.getPlaying11());
        }
        matchStatsByMatchId.put(match.getId(), stats);
        logger.info("Warmed up {} UserMatchStats for match {}", stats.size(), match.getId());

        reloadOverallStats();
    }

    private void reloadOverallStats() {
        overallStatsByUserId.clear();
        List<UserOverallStats> allOverall = userOverallStatsRepository.findAll();
        for (UserOverallStats o : allOverall) {
            if (o.getUserid() != null) {
                overallStatsByUserId.put(o.getUserid().getId(), o);
            }
        }
        overallDirty = false;
        logger.info("Loaded {} UserOverallStats from DB", overallStatsByUserId.size());
    }

    public List<UserMatchStats> getUserMatchStats(Integer matchId) {
        List<UserMatchStats> stats = matchStatsByMatchId.get(matchId);
        return stats != null ? stats : Collections.emptyList();
    }

    public Map<Integer, UserOverallStats> getOverallStatsByUserId() {
        return Collections.unmodifiableMap(new HashMap<>(overallStatsByUserId));
    }

    public void markMatchDirty(Integer matchId) {
        dirtyMatchIds.add(matchId);
    }

    public void markOverallDirty() {
        overallDirty = true;
    }

    public boolean hasDirtyData() {
        return !dirtyMatchIds.isEmpty() || overallDirty;
    }

    /**
     * Flush all dirty in-memory data to the database using batched JPA saveAll.
     * Hibernate batching (configured in application.properties) groups these into
     * batch_size chunks automatically.
     */
    @Transactional
    public void flushToDB() {
        if (!hasDirtyData()) return;

        long start = System.currentTimeMillis();

        for (Integer matchId : dirtyMatchIds) {
            List<UserMatchStats> stats = matchStatsByMatchId.get(matchId);
            if (stats != null && !stats.isEmpty()) {
                userMatchStatsRepository.saveAll(stats);
                logger.info("Flushed {} UserMatchStats for match {}", stats.size(), matchId);
            }
        }
        dirtyMatchIds.clear();

        if (overallDirty) {
            List<UserOverallStats> allOverall = new ArrayList<>(overallStatsByUserId.values());
            if (!allOverall.isEmpty()) {
                userOverallStatsRepository.saveAll(allOverall);
                logger.info("Flushed {} UserOverallStats", allOverall.size());
            }
            overallDirty = false;
        }

        logger.info("Cache flush completed in {} ms", System.currentTimeMillis() - start);
    }

    public boolean isWarmedUp(Integer matchId) {
        return matchStatsByMatchId.containsKey(matchId);
    }

    /**
     * Promotes prevpoints = totalpoints for all cached UserOverallStats.
     * Called once when a match ends, so that the next match's calculation
     * starts from the correct accumulated baseline.
     */
    public void promotePrevPoints() {
        int promoted = 0;
        for (UserOverallStats overall : overallStatsByUserId.values()) {
            Double total = overall.getTotalpoints();
            if (total != null) {
                overall.setPrevpoints(total);
                promoted++;
            }
        }
        overallDirty = true;
        logger.info("Promoted prevpoints = totalpoints for {} users", promoted);
    }

    public void evictMatch(Integer matchId) {
        matchStatsByMatchId.remove(matchId);
        dirtyMatchIds.remove(matchId);
    }

    public void evictAll() {
        matchStatsByMatchId.clear();
        overallStatsByUserId.clear();
        dirtyMatchIds.clear();
        overallDirty = false;
    }

    public Map<Integer, Integer> getAllMatchStatCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (var entry : matchStatsByMatchId.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    public int getOverallStatsCount() {
        return overallStatsByUserId.size();
    }
}
