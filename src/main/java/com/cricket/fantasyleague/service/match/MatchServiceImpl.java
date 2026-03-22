package com.cricket.fantasyleague.service.match;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;

@Service
public class MatchServiceImpl implements MatchService {

    private final CricketMasterDataDao dao;
    private final CricketEntityMapper cricketEntities;

    public MatchServiceImpl(CricketMasterDataDao dao, CricketEntityMapper cricketEntities) {
        this.dao = dao;
        this.cricketEntities = cricketEntities;
    }

    @Override
    public Match findMatchById(Integer matchid) {
        return dao.findMatchById(matchid)
                .map(cricketEntities::toMatch)
                .orElse(null);
    }

    @Override
    public List<Match> findMatchByDate(LocalDate currdate) {
        return dao.findMatchesByDate(currdate).stream()
                .map(cricketEntities::toMatch)
                .toList();
    }

    @Override
    public Match findUpcomingMatch(LocalDate currdate, LocalTime currtime) {
        return dao.findUpcomingMatch(currdate, currtime)
                .map(cricketEntities::toMatch)
                .orElse(null);
    }

    @Override
    public Match findPreviousMatch(Match currentMatch) {
        if (currentMatch == null || currentMatch.getDate() == null || currentMatch.getTime() == null) {
            return null;
        }
        return dao.findPreviousMatch(currentMatch.getDate(), currentMatch.getTime())
                .map(cricketEntities::toMatch)
                .orElse(null);
    }

    @Override
    public Match findLockedMatch(LocalDate currDate, LocalTime currTime) {
        return dao.findLockedMatch(currDate, currTime)
                .map(cricketEntities::toMatch)
                .orElse(null);
    }

    @Override
    public Match findNextUpcomingMatch() {
        return dao.findNextUpcomingMatch()
                .map(cricketEntities::toMatch)
                .orElse(null);
    }
}
