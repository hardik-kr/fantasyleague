package com.cricket.fantasyleague.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.repository.MatchRepository;

@Service
public class MatchServiceImpl implements MatchService 
{
    @Autowired
    private MatchRepository matchesRepository ;

    @Override
    public Match findMatchById(Integer matchid) 
    {
        Optional<Match> Match = matchesRepository.findById(matchid);
        return Match.orElse(null); // or throw an exception if you prefer
    }

    @Override
    public List<Match> findMatchByDate(LocalDate currdate) 
    {
        return matchesRepository.findByDateOrderByTimeAsc(currdate) ;
    }

    @Override
    public Match findUpcomingMatch(LocalDate currdate, LocalTime currtime) 
    {
        return matchesRepository.findMatchDateByTransfer(currdate, currtime) ;
    }

    @Override
    public Match findPreviousMatch(Integer matchnum) 
    {
        matchnum = matchnum-1 ;
        if(matchnum<=0)
            return null ;
            
        return matchesRepository.findByMatchnum(matchnum) ;
    }

    @Override
    public Match findLockedMatch(LocalDate currDate, LocalTime currTime) 
    {
        return matchesRepository.findLockMatch(currDate,currTime) ;
    }

    
}
