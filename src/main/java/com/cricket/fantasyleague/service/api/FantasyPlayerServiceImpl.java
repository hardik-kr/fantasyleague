package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;

@Service
public class FantasyPlayerServiceImpl implements FantasyPlayerService {

    private final CricketMasterDataDao dao;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;

    public FantasyPlayerServiceImpl(CricketMasterDataDao dao,
                                    FantasyPlayerConfigRepository fantasyPlayerConfigRepository) {
        this.dao = dao;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
    }

    @Override
    public List<PlayerResponse> getAllPlayersWithConfig(Integer leagueId) {
        List<PlayerWithTeamData> players = dao.findPlayersWithTeamByLeagueId(leagueId);
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);

        Map<Integer, FantasyPlayerConfig> configMap = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            configMap.put(cfg.getPlayerId(), cfg);
        }

        List<PlayerResponse> result = new ArrayList<>(players.size());
        for (PlayerWithTeamData p : players) {
            FantasyPlayerConfig cfg = configMap.get(p.id());
            result.add(new PlayerResponse(
                    p.id(), p.name(), p.role(),
                    p.teamId(), p.teamName(), p.teamShortName(),
                    cfg != null ? cfg.getCredit() : null,
                    cfg != null ? cfg.getOverseas() : false,
                    cfg != null ? cfg.getUncapped() : false,
                    cfg != null ? cfg.getTotalPoints() : 0.0,
                    cfg != null ? cfg.getIsActive() : true
            ));
        }
        return result;
    }
}
