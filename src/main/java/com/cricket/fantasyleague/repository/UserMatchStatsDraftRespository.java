package com.cricket.fantasyleague.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;

public interface UserMatchStatsDraftRespository extends JpaRepository<UserMatchStatsDraft,Integer> 
{
    UserMatchStatsDraft findByMatchidAndUserid(Match matchid,User userid) ;
}
