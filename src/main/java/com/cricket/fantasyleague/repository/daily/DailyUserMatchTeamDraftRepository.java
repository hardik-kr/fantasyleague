package com.cricket.fantasyleague.repository.daily;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeamDraft;

public interface DailyUserMatchTeamDraftRepository extends JpaRepository<DailyUserMatchTeamDraft, Long> {

    Optional<DailyUserMatchTeamDraft> findByMatchAndUser(Match match, User user);

    long countByMatch(Match match);

    @Query("SELECT d.id FROM DailyUserMatchTeamDraft d WHERE d.match = :match ORDER BY d.id")
    List<Long> findIdsByMatch(@Param("match") Match match, Pageable pageable);

    @Query("SELECT d FROM DailyUserMatchTeamDraft d LEFT JOIN FETCH d.playing11 WHERE d.id IN :ids ORDER BY d.id")
    List<DailyUserMatchTeamDraft> findAllByIdInWithPlaying11(@Param("ids") List<Long> ids);
}
