package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;

public interface PlayerPointsRepository extends JpaRepository<PlayerPoints,Integer>
{
    PlayerPoints findByPlayeridAndMatchid(Player pid,Match matchid) ;
    List<PlayerPoints> findByMatchid(Match matchid) ;
}
