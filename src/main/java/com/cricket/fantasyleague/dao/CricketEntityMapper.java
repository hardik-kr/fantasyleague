package com.cricket.fantasyleague.dao;

import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.TeamData;
import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.Team;

/**
 * Builds detached JPA entity instances from cricketapi read-model rows.
 * Avoids {@code EntityManager.find} against the fantasyleague datasource, where
 * {@code matches}/{@code team}/{@code player} may not exist as physical tables.
 */
@Component
public class CricketEntityMapper {

    private final CricketMasterDataDao dao;

    public CricketEntityMapper(CricketMasterDataDao dao) {
        this.dao = dao;
    }

    public Match toMatch(MatchData md) {
        if (md == null) {
            return null;
        }
        Match m = new Match();
        m.setId(md.id());
        m.setDate(md.date());
        m.setIsMatchComplete(md.isMatchComplete());
        m.setMatchState(MatchState.fromApiValue(md.matchState()));
        m.setMatchDesc(md.matchDesc());
        m.setResult(md.result());
        m.setTime(md.time());
        m.setTimezone(md.timezone());
        m.setVenue(md.venue());
        m.setToss(md.toss());
        m.setLeagueId(md.leagueId());
        m.setMomPlayerId(md.momPlayerId());
        if (md.teamAId() != null) {
            m.setTeamA(dao.findTeamById(md.teamAId()).map(this::toTeam).orElse(null));
        }
        if (md.teamBId() != null) {
            m.setTeamB(dao.findTeamById(md.teamBId()).map(this::toTeam).orElse(null));
        }
        return m;
    }

    public Team toTeam(TeamData td) {
        if (td == null) {
            return null;
        }
        return new Team(td.id(), td.name(), td.shortName(), td.leagueId());
    }

    public Player toPlayer(PlayerData pd) {
        if (pd == null) {
            return null;
        }
        return new Player(pd.id(), pd.name(), pd.role());
    }
}
