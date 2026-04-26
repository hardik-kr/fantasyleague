package com.cricket.fantasyleague.service.daily;

import java.util.List;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.payload.daily.DailyDraftResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchHistoryResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchPlayerPoolResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchSummary;
import com.cricket.fantasyleague.payload.daily.DailyMyTeamOverview;
import com.cricket.fantasyleague.payload.daily.DailyTeamRequest;
import com.cricket.fantasyleague.payload.daily.DailyTeamResponse;

/**
 * Read/write surface for the Daily Challenge mode.
 *
 * <p>Strict isolation from the season-long flow: this service has its own
 * tables (`daily_user_match_team*`) and never reads/writes any season-long
 * row. The {@link DailyTeamValidator} is the single rules authority.
 *
 * <p>Daily Challenge always targets the <b>single next upcoming match</b>
 * (mirrors the season-long {@code findNextUpcomingMatch} convention) — the
 * only daily-specific constraint is that the player pool is restricted to
 * the two sides playing that match.
 */
public interface DailyTeamService {

    /** @return the next upcoming non-started match wrapped with this user's daily flags, or {@code null} if none. */
    DailyMatchSummary getNextMatch(User user);

    /**
     * Player pool for the active daily match (server-resolved). No client
     * input — mirrors the season-long {@code GET /api/seasons/me/...} shape
     * and removes the URL-tampering attack class.
     */
    DailyMatchPlayerPoolResponse getActivePlayerPool();

    /**
     * The caller's draft for the active daily match (server-resolved).
     * Same {@code me/*} convention as season-long.
     */
    DailyDraftResponse getActiveDraft(User user);

    /**
     * Upsert the caller's draft for the active daily match.
     *
     * <p>The match is resolved server-side via
     * {@link com.cricket.fantasyleague.service.match.MatchService#findNextUpcomingMatch()}
     * — the client never names the match. This mirrors the season-long
     * {@code POST /api/seasons/transfer-update} convention and removes the
     * (already-validated) {@code matchId} path variable, eliminating the class
     * of "build a team for a future match by tampering the URL" attacks.
     */
    void upsertDraft(User user, DailyTeamRequest request);

    /**
     * Look up the locked team for an arbitrary {@code (userId, matchId)}
     * pair — the daily analogue of season-long's {@code POST /api/seasons/team}
     * with body {@code {userId, matchId}}. Used both to view your own past
     * teams and to view another user's team via a leaderboard click-through.
     *
     * <p>Returns {@code found=false} when no locked team exists for that
     * pair. The pre-match draft preview, when applicable, is served by
     * {@link #getActiveDraft(User)} rather than inlined here — same
     * separation season-long has between {@code /me/draft} and {@code /team}.
     */
    DailyTeamResponse getMyTeam(Long userId, Integer matchId);

    /**
     * One-shot bootstrap for the {@code /daily/my-team} screen — returns both
     * the caller's locked team for any currently in-flight match (live points)
     * <i>and</i> their draft for the next upcoming match (preview), so the FE
     * can populate the dropdown selector and render either side without
     * additional round-trips.
     *
     * <p>Either side may be {@code null}:
     * <ul>
     *   <li>{@code live == null}      — the caller has no locked team in any
     *       match currently in {@link com.cricket.fantasyleague.entity.enums.MatchState#IN_PROGRESS}
     *       or {@link com.cricket.fantasyleague.entity.enums.MatchState#DELAY}.</li>
     *   <li>{@code upcoming == null}  — there is no next-upcoming match (or the
     *       feature has nothing to surface, e.g. between IPL seasons).</li>
     * </ul>
     * Both null is a legitimate "no daily activity" empty state.
     */
    DailyMyTeamOverview getMyTeamOverview(User user);

    List<DailyMatchHistoryResponse> getHistory(User user);
}
