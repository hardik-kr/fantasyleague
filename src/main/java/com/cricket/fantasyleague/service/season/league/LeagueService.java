package com.cricket.fantasyleague.service.season.league;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.season.league.CreateLeagueRequest;
import com.cricket.fantasyleague.payload.season.league.LeagueDetailResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueLeaderboardPageResponse;
import com.cricket.fantasyleague.payload.season.league.LeaguePreviewResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueSummary;

/**
 * Business logic for the season-long Private Leagues feature.
 *
 * <p>All write methods are {@code @Transactional}; the cap check on
 * {@link #joinLeague(User, String)} is enforced atomically inside that
 * transaction by an {@code UPDATE ... WHERE member_count < max_members}.
 */
public interface LeagueService {

    /** Creates a new league with the supplied user as the first (and only) member. */
    LeagueResponse createLeague(User creator, CreateLeagueRequest request);

    /** Adds the user as a member of the league identified by {@code code}. */
    LeagueResponse joinLeague(User user, String code);

    /**
     * Removes the user from the league. If they were the last member the
     * league row is deleted in the same transaction.
     */
    void leaveLeague(User user, String code);

    /** All leagues the user belongs to, newest membership first. */
    List<LeagueSummary> getMyLeagues(User user);

    /** Detailed view (header + member list). Throws 404 if user is not a member. */
    LeagueDetailResponse getDetail(User user, String code);

    /**
     * Lightweight preview that does NOT require membership — used by the
     * share-link landing to power the "Join {name}?" / "You're already a
     * member" confirmation dialog before the user actually joins.
     */
    LeaguePreviewResponse getPreview(User user, String code);

    /** Paginated leaderboard restricted to league members. Throws 404 if user is not a member. */
    LeagueLeaderboardPageResponse getLeaderboard(User user, String code, int page, int size);
}
