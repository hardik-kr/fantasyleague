package com.cricket.fantasyleague.service.usermatchstats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;

@Service
public class UserMatchStatsServiceImpl implements UserMatchStatsService {

    private static final Logger logger = LoggerFactory.getLogger(UserMatchStatsServiceImpl.class);
    private static final int PARTITION_SIZE = 500;

    private final LiveMatchUserCache userCache;
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
    public Map<Long, Double> calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap) {
        List<UserMatchStats> allStats = userCache.getUserMatchStats(match.getId());
        if (allStats.isEmpty()) return Map.of();

        Integer leagueId = match.getLeagueId();
        Map<Integer, FantasyPlayerConfig> configByPlayerId = buildConfigMap(leagueId);

        List<UserMatchStats> userStats = allStats.stream()
                .filter(s -> s.getUserid() != null
                        && !"admin@gmail.com".equals(s.getUserid().getEmail()))
                .toList();

        List<List<UserMatchStats>> partitions = partition(userStats, PARTITION_SIZE);

        CompletableFuture<?>[] futures = partitions.stream()
                .map(chunk -> CompletableFuture.runAsync(() -> {
                    for (UserMatchStats stat : chunk) {
                        stat.setMatchpoints(calculateForUser(stat, playerPointsMap, configByPlayerId));
                    }
                }, taskExecutor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        userCache.saveMatchStats(match.getId(), allStats);
        userCache.markMatchDirty(match.getId());

        Map<Long, Double> matchPointsByUser = new HashMap<>(userStats.size());
        for (UserMatchStats stat : userStats) {
            matchPointsByUser.put(stat.getUserid().getId(),
                    stat.getMatchpoints() == null ? 0.0 : stat.getMatchpoints());
        }

        logger.info("User match points calculated for match {}: {} users", match.getId(), userStats.size());
        return matchPointsByUser;
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

    private Double calculateForUser(UserMatchStats stat, Map<Integer, Double> playerPointsMap,
                                    Map<Integer, FantasyPlayerConfig> configByPlayerId) {
        List<Player> team = stat.getPlaying11();
        if (team == null || team.isEmpty()) return 0.0;

        double total = 0.0;
        for (Player player : team) {
            Double base = playerPointsMap.get(player.getId());
            if (base == null) continue;
            FantasyPlayerConfig config = configByPlayerId.get(player.getId());
            total += applyMultipliers(stat, player, base, config);
        }
        return total;
    }

    private double applyMultipliers(UserMatchStats stat, Player player, double points,
                                    FantasyPlayerConfig config) {
        double result = points;

        Booster booster = stat.getBoosterused();
        if (booster != null && config != null) {
            result = applyBooster(booster, config.getType(), result);
        }

        if (stat.getCaptainid() != null && stat.getCaptainid().equals(player)) {
            result *= 2;
        }
        if (stat.getVicecaptainid() != null && stat.getVicecaptainid().equals(player)) {
            result *= 1.5;
        }
        if (stat.getTripleboosterplayerid() != null && stat.getTripleboosterplayerid().equals(player)) {
            result *= 3;
        }

        return result;
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

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
