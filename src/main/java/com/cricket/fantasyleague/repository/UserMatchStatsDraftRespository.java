package com.cricket.fantasyleague.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;

public interface UserMatchStatsDraftRespository extends JpaRepository<UserMatchStatsDraft, Long> 
{
    UserMatchStatsDraft findByMatchidAndUserid(Match matchid, User userid);
    List<UserMatchStatsDraft> findAllByMatchid(Match matchid);

    @Query("SELECT d.id FROM UserMatchStatsDraft d WHERE d.matchid = :match ORDER BY d.id")
    List<Long> findIdsByMatchid(@Param("match") Match match, Pageable pageable);

    @Query("SELECT d FROM UserMatchStatsDraft d LEFT JOIN FETCH d.playing11 " +
           "WHERE d.id IN :ids ORDER BY d.id")
    List<UserMatchStatsDraft> findAllByIdInWithPlaying11(@Param("ids") List<Long> ids);

    long countByMatchid(Match match);
}
