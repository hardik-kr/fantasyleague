package com.cricket.fantasyleague.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;

public interface UserOverallStatsRepository extends JpaRepository<UserOverallStats,Integer>
{
    UserOverallStats findByUserid(User userid) ;
}
