package com.cricket.fantasyleague.controller.daily;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.exception.FeatureDisabledException;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.daily.DailyDraftResponse;
import com.cricket.fantasyleague.payload.daily.DailyLeaderboardPageResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchHistoryResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchPlayerPoolResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchSummary;
import com.cricket.fantasyleague.payload.daily.DailyMyTeamOverview;
import com.cricket.fantasyleague.payload.daily.DailyTeamLookupRequest;
import com.cricket.fantasyleague.payload.daily.DailyTeamRequest;
import com.cricket.fantasyleague.payload.daily.DailyTeamResponse;
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.daily.DailyLeaderboardService;
import com.cricket.fantasyleague.service.daily.DailyTeamService;

/**
 * Public REST surface for Daily Challenge mode.
 *
 * <p>Daily mirrors the season-long convention of always targeting the single
 * <b>next upcoming match</b>; the only daily-specific rule is that the player
 * pool is restricted to that match's two sides.
 *
 * <p>Endpoints — laid out to mirror {@code /api/seasons/*} one-to-one so the
 * frontend speaks the same dialect to both modes:
 * <pre>
 *  GET  /api/daily/next-match                                → next upcoming match summary + user flags (or 204)
 *  GET  /api/daily/me/players                                → 22-player pool for the active match (server-resolved)
 *  GET  /api/daily/me/draft                                  → caller's draft for the active match (server-resolved)
 *  GET  /api/daily/me/overview                               → caller's live team + upcoming draft (one shot for /daily/my-team)
 *  POST /api/daily/team-update                               → upsert caller's draft for the active match (body: payload)
 *  POST /api/daily/team                                      → view a (userId, matchId) pair's locked team (body: ids)
 *  GET  /api/daily/match/{matchId}/leaderboard?page=&size=   → per-match leaderboard (paged)
 *  GET  /api/daily/me/history                                → caller's daily history
 * </pre>
 *
 * Mapping vs. season-long: {@code /me/draft} ↔ {@code /me/draft},
 * {@code /me/history} ↔ {@code /me/history}, {@code /team-update} ↔
 * {@code /transfer-update}, {@code /team} (body lookup) ↔ {@code /team}
 * (body lookup). {@code /me/players} is daily-specific because the player
 * pool is restricted to the two sides playing the active match (season-long
 * lets you pick from {@code GET /api/players}).
 *
 * Gated on the {@code fantasy.daily-challenge.enabled} feature flag — when
 * disabled every endpoint here responds with HTTP 503 Service Unavailable
 * (mapped from {@link FeatureDisabledException} in the global handler), so
 * clients can distinguish "feature is intentionally off right now" from
 * "real server error" and dashboards don't trip error-rate alerts.
 */
@RestController
@RequestMapping("/api/daily")
public class DailyChallengeController {

    private final DailyTeamService dailyTeamService;
    private final DailyLeaderboardService dailyLeaderboardService;
    private final UserRepository userRepository;
    private final boolean enabled;

    public DailyChallengeController(DailyTeamService dailyTeamService,
                                    DailyLeaderboardService dailyLeaderboardService,
                                    UserRepository userRepository,
                                    @Value("${fantasy.daily-challenge.enabled:false}") boolean enabled) {
        this.dailyTeamService = dailyTeamService;
        this.dailyLeaderboardService = dailyLeaderboardService;
        this.userRepository = userRepository;
        this.enabled = enabled;
    }

    @GetMapping("/next-match")
    public ResponseEntity<DailyMatchSummary> nextMatch() {
        ensureEnabled();
        DailyMatchSummary next = dailyTeamService.getNextMatch(getAuthenticatedUser());
        if (next == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(next);
    }

    /** Player pool for the active daily match — id-less, server-resolved. */
    @GetMapping("/me/players")
    public ResponseEntity<DailyMatchPlayerPoolResponse> myPlayers() {
        ensureEnabled();
        return ResponseEntity.ok(dailyTeamService.getActivePlayerPool());
    }

    /**
     * Caller's draft for the active daily match — id-less, server-resolved.
     * Mirrors {@code GET /api/seasons/me/draft}.
     */
    @GetMapping("/me/draft")
    public ResponseEntity<DailyDraftResponse> myDraft() {
        ensureEnabled();
        return ResponseEntity.ok(dailyTeamService.getActiveDraft(getAuthenticatedUser()));
    }

    /**
     * Combined "my team" snapshot — caller's locked team for any currently
     * in-flight match plus their draft for the next-upcoming one. Powers the
     * dropdown on the {@code /daily/my-team} screen so it can switch between
     * "live points" and "upcoming preview" without further round-trips. Either
     * side may be {@code null} (no live match the user is in / no upcoming
     * match scheduled); both null is a legitimate empty state.
     *
     * <p>Daily-only: season-long has a single rolling team so this combined
     * shape isn't needed there ({@code /season/my-team} reads
     * {@code /me/draft} alone).
     */
    @GetMapping("/me/overview")
    public ResponseEntity<DailyMyTeamOverview> myTeamOverview() {
        ensureEnabled();
        return ResponseEntity.ok(dailyTeamService.getMyTeamOverview(getAuthenticatedUser()));
    }

    /**
     * Upsert the caller's draft for the active daily match. The match is
     * resolved server-side (no {@code matchId} anywhere in the request) —
     * mirrors {@code POST /api/seasons/transfer-update}.
     */
    @PostMapping("/team-update")
    public ResponseEntity<ApiResponse> upsertTeam(@RequestBody DailyTeamRequest body) {
        ensureEnabled();
        dailyTeamService.upsertDraft(getAuthenticatedUser(), body);
        ApiResponse response = new ApiResponse("success", true,
                HttpStatus.CREATED.value(), HttpStatus.CREATED);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * View a (userId, matchId) pair's locked daily team. Both ids travel in
     * the body — same shape as {@code POST /api/seasons/team}. Used both for
     * "view my team for past match X" and "view user Y's team" (e.g. from a
     * leaderboard click-through).
     */
    @PostMapping("/team")
    public ResponseEntity<DailyTeamResponse> team(@RequestBody DailyTeamLookupRequest body) {
        ensureEnabled();
        return ResponseEntity.ok(dailyTeamService.getMyTeam(body.userId(), body.matchId()));
    }

    @GetMapping("/match/{matchId}/leaderboard")
    public ResponseEntity<DailyLeaderboardPageResponse> leaderboard(@PathVariable Integer matchId,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "50") int size) {
        ensureEnabled();
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(
                dailyLeaderboardService.getMatchLeaderboard(matchId, page, clampedSize, getAuthenticatedUser()));
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<DailyMatchHistoryResponse>> history() {
        ensureEnabled();
        return ResponseEntity.ok(dailyTeamService.getHistory(getAuthenticatedUser()));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void ensureEnabled() {
        if (!enabled) {
            throw new FeatureDisabledException("Daily Challenge mode is currently disabled");
        }
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        String username;
        if (principal instanceof UserDto dto) {
            username = dto.getUsername();
        } else {
            username = auth.getName();
        }
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + username);
        }
        return user;
    }
}
