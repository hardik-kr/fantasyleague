package com.cricket.fantasyleague.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;

public interface FantasyPlayerConfigRepository extends JpaRepository<FantasyPlayerConfig, Integer> 
{
    Optional<FantasyPlayerConfig> findByPlayerIdAndLeagueId(Integer playerId, Integer leagueId) ;
    List<FantasyPlayerConfig> findByLeagueId(Integer leagueId) ;
    List<FantasyPlayerConfig> findByPlayerId(Integer playerId) ;
    List<FantasyPlayerConfig> findByLeagueIdAndIsActiveTrue(Integer leagueId) ;
}
