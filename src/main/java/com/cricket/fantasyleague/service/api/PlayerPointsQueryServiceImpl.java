package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.payload.response.MatchPlayerPointsResponse;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;

@Service
public class PlayerPointsQueryServiceImpl implements PlayerPointsQueryService {

    private final PlayerPointsRepository playerPointsRepository;
    private final CricketMasterDataDao dao;

    public PlayerPointsQueryServiceImpl(PlayerPointsRepository playerPointsRepository,
                                        CricketMasterDataDao dao) {
        this.playerPointsRepository = playerPointsRepository;
        this.dao = dao;
    }

    @Override
    public List<MatchPlayerPointsResponse> getMatchPlayerPoints(Integer matchId) {
        List<PlayerPoints> ppList = playerPointsRepository.findByMatchId(matchId);

        List<Integer> playerIds = new ArrayList<>(ppList.size());
        for (PlayerPoints pp : ppList) {
            playerIds.add(pp.getPlayerId());
        }

        Map<Integer, String> nameMap = new HashMap<>(playerIds.size());
        Map<Integer, String> roleMap = new HashMap<>(playerIds.size());
        if (!playerIds.isEmpty()) {
            for (PlayerData pd : dao.findPlayersByIds(playerIds)) {
                nameMap.put(pd.id(), pd.name());
                roleMap.put(pd.id(), pd.role() != null ? pd.role().name() : null);
            }
        }

        List<MatchPlayerPointsResponse> result = new ArrayList<>(ppList.size());
        for (PlayerPoints pp : ppList) {
            result.add(new MatchPlayerPointsResponse(
                    pp.getPlayerId(),
                    nameMap.getOrDefault(pp.getPlayerId(), "Unknown"),
                    roleMap.get(pp.getPlayerId()),
                    pp.getPlayerpoints()
            ));
        }

        result.sort(Comparator.comparingDouble(
                (MatchPlayerPointsResponse r) -> r.points() != null ? r.points() : 0.0
        ).reversed());

        return result;
    }
}
