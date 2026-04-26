package com.cricket.fantasyleague.repository.daily;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;

public interface DailyUserMatchTeamRepository extends JpaRepository<DailyUserMatchTeam, Long> {

    Optional<DailyUserMatchTeam> findByMatchAndUser(Match match, User user);

    boolean existsByMatchAndUser(Match match, User user);

    long countByMatch(Match match);

    @Query("SELECT t.id FROM DailyUserMatchTeam t WHERE t.match = :match ORDER BY t.id")
    List<Long> findIdsByMatch(@Param("match") Match match, Pageable pageable);

    @Query("SELECT t FROM DailyUserMatchTeam t LEFT JOIN FETCH t.playing11 WHERE t.id IN :ids ORDER BY t.id")
    List<DailyUserMatchTeam> findAllByIdInWithPlaying11(@Param("ids") List<Long> ids);

    @Query("SELECT t FROM DailyUserMatchTeam t LEFT JOIN FETCH t.playing11 WHERE t.user = :user ORDER BY t.match.date DESC, t.match.time DESC")
    List<DailyUserMatchTeam> findHistoryByUser(@Param("user") User user);

    /**
     * Per-match leaderboard sort. Designed to use {@code idx_daily_match_points
     * (match_id, match_points DESC)} as a covering index for the
     * equality-then-ordered-range access pattern: equality on {@code match_id}
     * pins the index range, then a backward index scan returns rows already
     * sorted by {@code match_points DESC} — no filesort, no temp table,
     * regardless of the underlying row count.
     *
     * <p><b>Why no COALESCE on {@code matchPoints}</b>: the column is declared
     * {@code @Column(name = "match_points", nullable = false)} with a default
     * of {@code 0.0}, so wrapping it in {@code COALESCE(t.matchPoints, 0)}
     * adds no semantic value but makes the {@code ORDER BY} expression
     * non-sargable for some MariaDB optimizer paths — which would silently
     * drop us into filesort at 100K rows.
     */
    @Query("SELECT t FROM DailyUserMatchTeam t WHERE t.match.id = :matchId ORDER BY t.matchPoints DESC, t.id ASC")
    Page<DailyUserMatchTeam> findRankedByMatchId(@Param("matchId") Integer matchId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM DailyUserMatchTeam t WHERE t.match.id = :matchId")
    long countByMatchId(@Param("matchId") Integer matchId);

    /**
     * Count of rows ranked above the given user (their competition rank for
     * the match is {@code countAbove + 1}). Same index target as
     * {@link #findRankedByMatchId}: equality on {@code match_id} + range on
     * {@code match_points} resolves entirely inside
     * {@code idx_daily_match_points} as an index-condition pushdown.
     *
     * <p>Param coalesce is preserved (the caller may legitimately pass
     * {@code null} for a not-yet-scored team), but the column-side comparison
     * is bare so the index range scan stays sargable.
     */
    @Query("SELECT COUNT(t) FROM DailyUserMatchTeam t WHERE t.match.id = :matchId AND t.matchPoints > COALESCE(:points, 0)")
    long countAboveByMatchId(@Param("matchId") Integer matchId, @Param("points") Double points);

    /** Bulk idempotency check during match lock: returns the user ids that already have a locked team for this match. */
    @Query("SELECT t.user.id FROM DailyUserMatchTeam t WHERE t.match = :match AND t.user.id IN (:userIds)")
    List<Long> findExistingUserIds(@Param("match") Match match, @Param("userIds") List<Long> userIds);

    /** Page locked-team ids for a match by primary key — used by the live points recompute pipeline. */
    @Query("SELECT t.id FROM DailyUserMatchTeam t WHERE t.match.id = :matchId AND t.id > :afterId ORDER BY t.id ASC")
    List<Long> findIdsByMatchIdAfter(@Param("matchId") Integer matchId,
                                     @Param("afterId") Long afterId,
                                     Pageable pageable);

    /**
     * The caller's locked-team ids for any match currently in one of the
     * given "live" states (typically {@link MatchState#IN_PROGRESS} +
     * {@link MatchState#DELAY}), ordered most-recently-started first.
     * Powers the {@code /api/daily/me/overview} "is there a match in flight
     * for this user right now?" probe — pass {@code Pageable.ofSize(1)}
     * when you only need the single most-recently-started one (the common
     * case; only IPL doubleheaders can return more).
     *
     * <p>This is intentionally <i>id-only</i>. Combining a {@code Pageable}
     * with {@code JOIN FETCH t.playing11} triggers Hibernate's
     * {@code HHH90003004} warning — pagination over a joined element
     * collection forces in-memory trimming because each parent ends up as
     * 11 result rows. Two-query pattern (mirrors {@link #findIdsByMatch}
     * + {@link #findAllByIdInWithPlaying11}): page the ids here, then load
     * the entities with {@code playing11} via
     * {@link #findAllByIdInWithPlaying11(List)}.
     */
    @Query("""
            SELECT t.id FROM DailyUserMatchTeam t
            WHERE t.user = :user
              AND t.match.matchState IN :states
            ORDER BY t.match.date DESC, t.match.time DESC
            """)
    List<Long> findLiveTeamIdsForUser(@Param("user") User user,
                                      @Param("states") Collection<MatchState> states,
                                      Pageable pageable);
}
