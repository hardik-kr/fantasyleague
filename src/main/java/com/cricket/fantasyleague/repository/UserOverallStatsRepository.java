package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;

public interface UserOverallStatsRepository extends JpaRepository<UserOverallStats, Long>
{
    UserOverallStats findByUserid(User userid) ;

    List<UserOverallStats> findAllByUseridIn(List<User> users);

    @Query("SELECT u FROM UserOverallStats u WHERE u.userid.id IN (:userIds)")
    List<UserOverallStats> findAllByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query("SELECT u FROM UserOverallStats u ORDER BY COALESCE(u.totalpoints, 0) DESC")
    Page<UserOverallStats> findAllRanked(Pageable pageable);

    @Query("SELECT COUNT(u) FROM UserOverallStats u WHERE COALESCE(u.totalpoints, 0) > COALESCE(:points, 0)")
    long countUsersAbove(@Param("points") Double points);

    @Transactional
    @Modifying
    @Query(value = "UPDATE user_overall_stats uos" +
            " LEFT JOIN (SELECT user_id, COALESCE(SUM(matchpoints), 0) AS correct_pts" +
            "            FROM user_match_stats GROUP BY user_id) ums" +
            " ON uos.user_id = ums.user_id" +
            " SET uos.totalpoints = COALESCE(ums.correct_pts, 0)," +
            "     uos.prevpoints = COALESCE(ums.correct_pts, 0)" +
            " WHERE ABS(COALESCE(uos.totalpoints, 0) - COALESCE(ums.correct_pts, 0)) > 0.01",
            nativeQuery = true)
    int syncTotalPointsFromMatchStats();
}
