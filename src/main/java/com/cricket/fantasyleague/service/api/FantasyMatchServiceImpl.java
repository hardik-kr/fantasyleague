package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.TeamBrief;

@Service
public class FantasyMatchServiceImpl implements FantasyMatchService {

    private final CricketMasterDataDao dao;
    private final CricketEntityMapper mapper;

    public FantasyMatchServiceImpl(CricketMasterDataDao dao, CricketEntityMapper mapper) {
        this.dao = dao;
        this.mapper = mapper;
    }

    @Override
    public List<MatchResponse> getAllMatchesWithTeams() {
        List<MatchData> allMatches = dao.findAllMatches();
        Map<Integer, TeamBrief> teamCache = new HashMap<>();
        List<MatchResponse> result = new ArrayList<>(allMatches.size());

        for (MatchData md : allMatches) {
            result.add(new MatchResponse(
                    md.id(), md.date(), md.time(), md.venue(),
                    md.toss(), md.result(), md.isMatchComplete(),
                    md.matchState(), md.matchDesc(),
                    resolveTeam(md.teamAId(), teamCache),
                    resolveTeam(md.teamBId(), teamCache)
            ));
        }
        return result;
    }

    private TeamBrief resolveTeam(Integer teamId, Map<Integer, TeamBrief> cache) {
        if (teamId == null) return null;
        return cache.computeIfAbsent(teamId, id -> {
            Team t = dao.findTeamById(id).map(mapper::toTeam).orElse(null);
            if (t == null) return new TeamBrief(id, null, null);
            return new TeamBrief(t.getId(), t.getName(), t.getShortName());
        });
    }
}
