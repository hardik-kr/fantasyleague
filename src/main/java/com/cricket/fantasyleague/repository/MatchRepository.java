package com.cricket.fantasyleague.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.Match;

public interface MatchRepository extends JpaRepository<Match,Integer>
{
    List<Match> findByDateOrderByTimeAsc(LocalDate date) ;
    
    @Query("SELECT m FROM Match m "+
    "WHERE ( m.date > :currDate OR (m.date = :currDate AND m.time > :currTime)) "+
    "ORDER BY m.date ASC, m.time ASC "+
    "LIMIT 1")
    Match findMatchDateByTransfer(@Param("currDate") LocalDate currDate,
                                @Param("currTime") LocalTime currTime) ;
                            
    Match findByMatchnum(Integer matchnum) ;

    @Query("SELECT m FROM Match m "+
    "WHERE (m.date = :currDate AND m.time <= :currTime) "+
    "ORDER BY m.time DESC "+
    "LIMIT 1")
    Match findLockMatch(LocalDate currDate, LocalTime currTime);
                            
}
