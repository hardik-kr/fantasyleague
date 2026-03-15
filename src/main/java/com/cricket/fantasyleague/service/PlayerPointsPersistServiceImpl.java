package com.cricket.fantasyleague.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.util.AppConstants;

import jakarta.persistence.EntityManager;

@Service
public class PlayerPointsPersistServiceImpl {

    private final PlayerPointsRepository playerPointsRepository;
    private final CricketMasterDataDao dao;
    private final EntityManager em;

    public PlayerPointsPersistServiceImpl(PlayerPointsRepository playerPointsRepository,
                                          CricketMasterDataDao dao,
                                          EntityManager em) {
        this.playerPointsRepository = playerPointsRepository;
        this.dao = dao;
        this.em = em;
    }

    public List<PlayerPoints> findPlayerPointsByMatch(Match match) {
        return playerPointsRepository.findByMatchid(match);
    }

    public void saveAllPlayerPoints(List<PlayerPoints> records) {
        try {
            playerPointsRepository.saveAll(records);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.PLAYERPOINTS,
                    cause.getMessage()));
        }
    }

    public Optional<Player> findPlayerByNameAndTeam(String playerName, String teamName) {
        return dao.findPlayerByNameAndTeam(playerName, teamName)
                .map(pd -> em.find(Player.class, pd.id()));
    }
}
