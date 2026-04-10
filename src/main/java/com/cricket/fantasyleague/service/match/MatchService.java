package com.cricket.fantasyleague.service.match;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.cricket.fantasyleague.entity.table.Match;

public interface MatchService 
{
    Match findMatchById(Integer matchid) ;
    List<Match> findMatchByDate(LocalDate currdate);
    List<Match> findCandidateMatches(LocalDate today);
    Match findUpcomingMatch(LocalDate currdate,LocalTime currtime) ;
    Match findPreviousMatch(Match currentMatch);
    Match findLockedMatch(LocalDate currDate, LocalTime currTime);
    Match findNextUpcomingMatch();
}
