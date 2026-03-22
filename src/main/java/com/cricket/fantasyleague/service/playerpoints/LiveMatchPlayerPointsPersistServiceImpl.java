package com.cricket.fantasyleague.service.playerpoints;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class LiveMatchPlayerPointsPersistServiceImpl {

    private final PlayerPointsRepository playerPointsRepository;
    private final CricketMasterDataDao dao;
    private final CricketEntityMapper cricketEntities;

    public LiveMatchPlayerPointsPersistServiceImpl(PlayerPointsRepository playerPointsRepository,
                                                   CricketMasterDataDao dao,
                                                   CricketEntityMapper cricketEntities) {
        this.playerPointsRepository = playerPointsRepository;
        this.dao = dao;
        this.cricketEntities = cricketEntities;
    }

    public List<PlayerPoints> findPlayerPointsByMatch(Match match) {
        return playerPointsRepository.findByMatchId(match.getId());
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
                .map(cricketEntities::toPlayer);
    }

    public Optional<Player> findPlayerById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return dao.findPlayerById(id).map(cricketEntities::toPlayer);
    }

    public Map<Integer, Player> findPlayersByIds(List<Integer> ids) {
        return dao.findPlayersByIds(ids).stream()
                .map(cricketEntities::toPlayer)
                .filter(p -> p != null && p.getId() != null)
                .collect(Collectors.toMap(Player::getId, p -> p));
    }
}
