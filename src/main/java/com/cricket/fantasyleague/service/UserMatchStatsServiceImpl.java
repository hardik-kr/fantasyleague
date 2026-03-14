package com.cricket.fantasyleague.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;

/**
 * Calculates per-user match points in parallel.
 *
 * Key optimizations:
 *   - Player points map is passed in (no DB query; was previously a separate DB read)
 *   - Users are processed concurrently via CompletableFuture (no inter-user dependency)
 *   - Single batch saveAll at the end
 */
@Service
public class UserMatchStatsServiceImpl implements UserMatchStatsService {

    private static final Logger logger = LoggerFactory.getLogger(UserMatchStatsServiceImpl.class);

    private final UserMatchStatsRespository userMatchStatsRepository;
    private final Executor taskExecutor;

    public UserMatchStatsServiceImpl(UserMatchStatsRespository userMatchStatsRepository,
                                     @Qualifier("fantasyTaskExecutor") Executor taskExecutor) {
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void calcMatchUserPointsData(Match match, Map<Integer, Double> playerPointsMap) {
        List<UserMatchStats> allStats = userMatchStatsRepository.findByMatchid(match);
        if (allStats.isEmpty()) return;

        List<UserMatchStats> userStats = allStats.stream()
                .filter(s -> s.getUserid() != null
                        && !"admin@gmail.com".equals(s.getUserid().getEmail()))
                .toList();

        CompletableFuture<?>[] futures = userStats.stream()
                .map(stat -> CompletableFuture.runAsync(
                        () -> stat.setMatchpoints(calculateForUser(stat, playerPointsMap)),
                        taskExecutor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();

        userMatchStatsRepository.saveAll(allStats);
        logger.info("User match points saved for match {}: {} users", match.getId(), userStats.size());
    }

    private Double calculateForUser(UserMatchStats stat, Map<Integer, Double> playerPointsMap) {
        List<Player> team = stat.getPlaying11();
        if (team == null || team.isEmpty()) return 0.0;

        double total = 0.0;
        for (Player player : team) {
            Double base = playerPointsMap.get(player.getId());
            if (base == null) continue;
            total += applyMultipliers(stat, player, base);
        }
        return total;
    }

    private double applyMultipliers(UserMatchStats stat, Player player, double points) {
        double result = points;

        Booster booster = stat.getBoosterused();
        if (booster != null) {
            result = applyBooster(booster, player, result);
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

    private double applyBooster(Booster booster, Player player, double points) {
        return switch (booster) {
            case DOUBLE_UP -> points * 2;
            case POWER_KEEPER -> player.getType() == PlayerType.KEEPER ? points * 2 : points;
            case POWER_BATTER -> player.getType() == PlayerType.BATTER ? points * 2 : points;
            case POWER_ALLROUNDER -> player.getType() == PlayerType.ALLROUNDER ? points * 2 : points;
            case POWER_BOWLER -> player.getType() == PlayerType.BOWLER ? points * 2 : points;
            default -> points;
        };
    }
}
