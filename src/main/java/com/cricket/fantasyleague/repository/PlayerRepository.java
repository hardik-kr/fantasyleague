package com.cricket.fantasyleague.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.Player;

public interface PlayerRepository extends JpaRepository<Player,Integer> 
{
    List<Player> findByTeamidName(String country) ;  

    @Query("SELECT p from Player p "+
    "JOIN p.teamid t "+
    "WHERE p.name LIKE %:playerName% AND t.name = :country")
    Optional<Player> findByNameTeamidName(@Param("playerName") String playerName, @Param("country") String country) ;
}
