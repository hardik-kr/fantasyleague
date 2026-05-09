package com.cricket.fantasyleague.repository.season.league;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.table.season.league.PrivateLeague;

public interface PrivateLeagueRepository extends JpaRepository<PrivateLeague, Long> {

    Optional<PrivateLeague> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomically increments {@code member_count} only if the league has free
     * capacity. Returns the number of rows affected: {@code 1} on success,
     * {@code 0} if the league was already full (or no longer exists).
     *
     * <p>This is the core race-safe cap check: InnoDB's row lock serialises
     * concurrent calls so two simultaneous joiners never both pass the
     * {@code member_count < max_members} predicate.
     */
    @Modifying
    @Query(value = "UPDATE private_league " +
            "SET member_count = member_count + 1 " +
            "WHERE id = :leagueId AND member_count < max_members",
            nativeQuery = true)
    int incrementMemberCountIfNotFull(@Param("leagueId") Long leagueId);

    /**
     * Decrements {@code member_count} but never below zero. Used when a
     * member leaves; the caller is responsible for deciding whether to also
     * delete the league row (i.e. when the count would reach zero).
     */
    @Modifying
    @Query(value = "UPDATE private_league " +
            "SET member_count = member_count - 1 " +
            "WHERE id = :leagueId AND member_count > 0",
            nativeQuery = true)
    int decrementMemberCount(@Param("leagueId") Long leagueId);
}
