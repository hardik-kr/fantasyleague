package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import com.cricket.fantasyleague.entity.table.PlayerPoints;

import jakarta.persistence.QueryHint;

public interface PlayerPointsRepository extends JpaRepository<PlayerPoints, Long>
{
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<PlayerPoints> findByMatchId(Integer matchId);
}
