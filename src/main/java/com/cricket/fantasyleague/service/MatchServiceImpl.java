package com.cricket.fantasyleague.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;

import jakarta.persistence.EntityManager;

@Service
public class MatchServiceImpl implements MatchService {

    private final CricketMasterDataDao dao;
    private final EntityManager em;

    public MatchServiceImpl(CricketMasterDataDao dao, EntityManager em) {
        this.dao = dao;
        this.em = em;
    }

    @Override
    public Match findMatchById(Integer matchid) {
        return dao.findMatchById(matchid)
                .map(md -> em.find(Match.class, md.id()))
                .orElse(null);
    }

    @Override
    public List<Match> findMatchByDate(LocalDate currdate) {
        return dao.findMatchesByDate(currdate).stream()
                .map(md -> em.find(Match.class, md.id()))
                .toList();
    }

    @Override
    public Match findUpcomingMatch(LocalDate currdate, LocalTime currtime) {
        return dao.findUpcomingMatch(currdate, currtime)
                .map(md -> em.find(Match.class, md.id()))
                .orElse(null);
    }

    @Override
    public Match findPreviousMatch(Integer matchnum) {
        matchnum = matchnum - 1;
        if (matchnum <= 0) return null;
        return dao.findByMatchnum(matchnum)
                .map(md -> em.find(Match.class, md.id()))
                .orElse(null);
    }

    @Override
    public Match findLockedMatch(LocalDate currDate, LocalTime currTime) {
        return dao.findLockedMatch(currDate, currTime)
                .map(md -> em.find(Match.class, md.id()))
                .orElse(null);
    }
}
