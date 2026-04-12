package com.cricket.fantasyleague.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;

public interface UserOverallStatsRepository extends JpaRepository<UserOverallStats,Integer>
{
    UserOverallStats findByUserid(User userid) ;

    @Transactional
    @Modifying
    @Query(value = "UPDATE user_overall_stats uos" +
            " JOIN (SELECT user_id, COALESCE(SUM(matchpoints), 0) AS correct_pts" +
            "       FROM user_match_stats GROUP BY user_id) ums" +
            " ON uos.user_id = ums.user_id" +
            " SET uos.totalpoints = ums.correct_pts, uos.prevpoints = ums.correct_pts" +
            " WHERE ABS(COALESCE(uos.totalpoints, 0) - ums.correct_pts) > 0.01", nativeQuery = true)
    int syncTotalPointsFromMatchStats();
}
