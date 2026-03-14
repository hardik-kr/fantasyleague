package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;

public interface UserMatchStatsRespository extends JpaRepository<UserMatchStats,Integer>
{
    UserMatchStats findByMatchidAndUserid(Match matchid,User userid) ;
    List<UserMatchStats> findByMatchid(Match matchid) ;
}
