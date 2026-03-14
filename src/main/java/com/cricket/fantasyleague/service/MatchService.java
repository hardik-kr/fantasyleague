package com.cricket.fantasyleague.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.cricket.fantasyleague.entity.table.Match;

public interface MatchService 
{
    Match findMatchById(Integer matchid) ;
    List<Match> findMatchByDate(LocalDate currdate);    
    Match findUpcomingMatch(LocalDate currdate,LocalTime currtime) ;
    Match findPreviousMatch(Integer matchnum);
    Match findLockedMatch(LocalDate currDate, LocalTime currTime);
}
