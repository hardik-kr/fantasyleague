package com.cricket.fantasyleague.repository.season.league;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.league.PrivateLeague;
import com.cricket.fantasyleague.entity.table.season.league.PrivateLeagueMember;

public interface PrivateLeagueMemberRepository extends JpaRepository<PrivateLeagueMember, Long> {

    boolean existsByPrivateLeagueAndUser(PrivateLeague privateLeague, User user);

    Optional<PrivateLeagueMember> findByPrivateLeagueAndUser(PrivateLeague privateLeague, User user);

    /**
     * Returns the list of member user-ids for a given league, used by the
     * leaderboard to filter {@code UserOverallStats}.
     */
    @Query("SELECT m.user.id FROM PrivateLeagueMember m WHERE m.privateLeague = :league")
    List<Long> findUserIdsByPrivateLeague(@Param("league") PrivateLeague league);

    /**
     * Lists every league the given user belongs to, newest membership first.
     * Used by the {@code GET /api/seasons/leagues/me} endpoint.
     */
    @Query("SELECT m FROM PrivateLeagueMember m JOIN FETCH m.privateLeague " +
            "WHERE m.user = :user ORDER BY m.joinedAt DESC")
    List<PrivateLeagueMember> findByUserOrderByJoinedAtDesc(@Param("user") User user);

    /** All members of a league ordered by oldest-first (creator usually first). */
    @Query("SELECT m FROM PrivateLeagueMember m WHERE m.privateLeague = :league ORDER BY m.joinedAt ASC")
    List<PrivateLeagueMember> findByPrivateLeagueOrderByJoinedAtAsc(@Param("league") PrivateLeague league);

    @Modifying
    @Query("DELETE FROM PrivateLeagueMember m WHERE m.privateLeague = :league AND m.user = :user")
    int deleteByPrivateLeagueAndUser(@Param("league") PrivateLeague league, @Param("user") User user);
}
