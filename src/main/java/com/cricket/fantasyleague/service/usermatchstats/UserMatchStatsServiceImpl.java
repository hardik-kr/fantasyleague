package com.cricket.fantasyleague.service.usermatchstats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.cache.dto.CachedUserMatchStats;
import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;

@Service
public class UserMatchStatsServiceImpl implements UserMatchStatsService {

    private static final Logger logger = LoggerFactory.getLogger(UserMatchStatsServiceImpl.class);
    private static final int CHUNK_SIZE = 1000;
    private static final String ADMIN_EMAIL = "admin@gmail.com";

    private final LiveMatchUserCache userCache;
    @SuppressWarnings("unused") // retained for future per-chunk parallelism
    private final Executor taskExecutor;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;

    public UserMatchStatsServiceImpl(LiveMatchUserCache userCache,
                                     @Qualifier("fantasyTaskExecutor") Executor taskExecutor,
                                     FantasyPlayerConfigRepository fantasyPlayerConfigRepository) {
        this.userCache = userCache;
        this.taskExecutor = taskExecutor;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
    }

    @Override
    public void calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap) {
        Integer leagueId = match.getLeagueId();
        Map<Integer, FantasyPlayerConfig> configByPlayerId = buildConfigMap(leagueId);

        AtomicInteger processed = new AtomicInteger();

        userCache.forEachMatchStatsChunk(match.getId(), CHUNK_SIZE, chunk -> {
            List<CachedUserMatchStats> updated = new ArrayList<>(chunk.size());
            for (CachedUserMatchStats dto : chunk) {
                if (dto.userId() == null) continue;
                if (ADMIN_EMAIL.equals(dto.userEmail())) continue;
                double mp = calculateForDto(dto, playerPointsMap, configByPlayerId);
                updated.add(dto.withMatchpoints(mp));
            }
            userCache.saveMatchStatsChunk(match.getId(), updated);
            processed.addAndGet(updated.size());
        });

        userCache.markMatchDirty(match.getId());
        logger.info("User match points streamed for match {}: {} users (chunk={})",
                match.getId(), processed.get(), CHUNK_SIZE);
    }

    private Map<Integer, FantasyPlayerConfig> buildConfigMap(Integer leagueId) {
        if (leagueId == null) return Map.of();
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);
        Map<Integer, FantasyPlayerConfig> map = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            map.put(cfg.getPlayerId(), cfg);
        }
        return map;
    }

    /**
     * Operates purely on Integer IDs inside the DTO — no Player/User/Match stubs
     * are allocated per user. This is the hot loop; it runs ~100K times per tick.
     */
    private double calculateForDto(CachedUserMatchStats dto,
                                   Map<Integer, Double> playerPointsMap,
                                   Map<Integer, FantasyPlayerConfig> configByPlayerId) {
        List<Integer> team = dto.playing11Ids();
        if (team == null || team.isEmpty()) return 0.0;

        Booster booster = dto.boosterOrdinal() != null
                ? Booster.values()[dto.boosterOrdinal()]
                : null;

        double total = 0.0;
        for (Integer playerId : team) {
            Double base = playerPointsMap.get(playerId);
            if (base == null) continue;

            FantasyPlayerConfig cfg = configByPlayerId.get(playerId);
            double pts = base;
            if (booster != null && cfg != null) {
                pts = applyBooster(booster, cfg.getType(), pts);
            }
            if (playerId.equals(dto.captainId())) {
                pts *= 2;
            }
            if (playerId.equals(dto.vicecaptainId())) {
                pts *= 1.5;
            }
            if (playerId.equals(dto.tripleBoosterId())) {
                pts *= 3;
            }
            total += pts;
        }
        return total;
    }

    private double applyBooster(Booster booster, PlayerType playerType, double points) {
        if (playerType == null) return points;
        return switch (booster) {
            case DOUBLE_UP -> points * 2;
            case POWER_KEEPER -> playerType == PlayerType.KEEPER ? points * 2 : points;
            case POWER_BATTER -> playerType == PlayerType.BATTER ? points * 2 : points;
            case POWER_ALLROUNDER -> playerType == PlayerType.ALLROUNDER ? points * 2 : points;
            case POWER_BOWLER -> playerType == PlayerType.BOWLER ? points * 2 : points;
            default -> points;
        };
    }
}
