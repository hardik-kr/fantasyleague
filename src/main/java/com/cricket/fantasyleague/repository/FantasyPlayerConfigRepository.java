package com.cricket.fantasyleague.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;

public interface FantasyPlayerConfigRepository extends JpaRepository<FantasyPlayerConfig, Long> 
{
    Optional<FantasyPlayerConfig> findByPlayerIdAndLeagueId(Integer playerId, Integer leagueId) ;
    List<FantasyPlayerConfig> findByLeagueId(Integer leagueId) ;
    List<FantasyPlayerConfig> findByPlayerId(Integer playerId) ;
    List<FantasyPlayerConfig> findByLeagueIdAndIsActiveTrue(Integer leagueId) ;

    @Transactional
    @Modifying
    @Query(value = "UPDATE fantasy_player_config fpc" +
            " LEFT JOIN (SELECT player_id, COALESCE(SUM(playerpoints), 0) AS correct_pts" +
            "            FROM player_points GROUP BY player_id) pp" +
            " ON fpc.player_id = pp.player_id" +
            " SET fpc.total_points = COALESCE(pp.correct_pts, 0)" +
            " WHERE ABS(COALESCE(fpc.total_points, 0) - COALESCE(pp.correct_pts, 0)) > 0.01",
            nativeQuery = true)
    int syncTotalPointsFromPlayerPoints();
}
