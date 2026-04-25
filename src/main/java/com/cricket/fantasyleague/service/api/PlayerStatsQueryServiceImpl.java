package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.TeamData;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.payload.response.PlayerMatchPointsResponse;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;

@Service
public class PlayerStatsQueryServiceImpl implements PlayerStatsQueryService {

    private final PlayerPointsRepository playerPointsRepository;
    private final CricketMasterDataDao dao;

    public PlayerStatsQueryServiceImpl(PlayerPointsRepository playerPointsRepository,
                                       CricketMasterDataDao dao) {
        this.playerPointsRepository = playerPointsRepository;
        this.dao = dao;
    }

    @Override
    public List<PlayerMatchPointsResponse> getPointsHistory(Integer playerId) {
        List<PlayerPoints> rows = playerPointsRepository.findByPlayerId(playerId);
        if (rows.isEmpty()) return List.of();

        Set<Integer> matchIds = new HashSet<>(rows.size());
        for (PlayerPoints pp : rows) {
            if (pp.getMatchId() != null) matchIds.add(pp.getMatchId());
        }

        Map<Integer, MatchData> matchMap = new HashMap<>(matchIds.size());
        for (MatchData md : dao.findMatchesByIds(new ArrayList<>(matchIds))) {
            matchMap.put(md.id(), md);
        }

        Map<Integer, String> teamShortNameCache = new HashMap<>();

        List<PlayerMatchPointsResponse> result = new ArrayList<>(rows.size());
        for (PlayerPoints pp : rows) {
            MatchData md = matchMap.get(pp.getMatchId());
            if (md == null) {
                result.add(new PlayerMatchPointsResponse(
                        pp.getMatchId(), null, null, null, null, null, null, pp.getPlayerpoints()
                ));
                continue;
            }
            result.add(new PlayerMatchPointsResponse(
                    md.id(),
                    md.matchDesc(),
                    md.date(),
                    md.time(),
                    resolveTeamShortName(md.teamAId(), teamShortNameCache),
                    resolveTeamShortName(md.teamBId(), teamShortNameCache),
                    md.isMatchComplete(),
                    pp.getPlayerpoints()
            ));
        }

        result.sort(Comparator
                .comparing(PlayerMatchPointsResponse::date,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PlayerMatchPointsResponse::time,
                        Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    private String resolveTeamShortName(Integer teamId, Map<Integer, String> cache) {
        if (teamId == null) return null;
        return cache.computeIfAbsent(teamId,
                id -> dao.findTeamById(id).map(TeamData::shortName).orElse(null));
    }
}
