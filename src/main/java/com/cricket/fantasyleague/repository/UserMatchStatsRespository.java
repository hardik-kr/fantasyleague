package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;

public interface UserMatchStatsRespository extends JpaRepository<UserMatchStats, Long>
{
    UserMatchStats findByMatchidAndUserid(Match matchid,User userid) ;
    List<UserMatchStats> findByMatchid(Match matchid) ;
    List<UserMatchStats> findByUserid(User userid) ;
    boolean existsByMatchid(Match matchid) ;
    long countByMatchid(Match matchid) ;

    /**
     * Returns the user ids for which UserMatchStats already exists for the given match.
     * Used by lockMatchTeam to skip users that were already locked in a prior crashed attempt,
     * making booster/transfer decrement idempotent at per-row granularity.
     */
    @Query("SELECT u.userid.id FROM UserMatchStats u WHERE u.matchid = :match AND u.userid.id IN (:userIds)")
    List<Long> findLockedUserIds(@Param("match") Match match, @Param("userIds") List<Long> userIds);

    @Query("SELECT u.userid.id, COALESCE(SUM(u.matchpoints), 0) FROM UserMatchStats u GROUP BY u.userid.id")
    List<Object[]> sumMatchPointsByUser();

    /**
     * Committed-total snapshot: SUM(matchpoints) per user from matches that are NOT currently live.
     * Scoped to the supplied active user ids (typically the users who drafted for the current match).
     */
    @Query("SELECT u.userid.id, COALESCE(SUM(u.matchpoints), 0) FROM UserMatchStats u " +
            "WHERE u.userid.id IN (:userIds) AND u.matchid.id NOT IN (:excludedMatchIds) " +
            "GROUP BY u.userid.id")
    List<Object[]> sumMatchPointsByUserExcludingMatches(@Param("userIds") List<Long> userIds,
                                                        @Param("excludedMatchIds") List<Integer> excludedMatchIds);
}
