package com.cricket.fantasyleague.controller.season.league;

import java.util.List;

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
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.payload.season.league.CreateLeagueRequest;
import com.cricket.fantasyleague.payload.season.league.LeagueDetailResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueLeaderboardPageResponse;
import com.cricket.fantasyleague.payload.season.league.LeaguePreviewResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueResponse;
import com.cricket.fantasyleague.payload.season.league.LeagueSummary;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.season.league.LeagueService;

import jakarta.validation.Valid;

/**
 * REST endpoints for the season-long Private Leagues feature. Mirrors the
 * authentication / current-user resolution pattern of
 * {@link com.cricket.fantasyleague.controller.season.SeasonController}.
 *
 * <p>All routes live under {@code /api/seasons/leagues} which is already
 * covered by the {@code /api/seasons/**} matcher in {@code SecurityConfig}
 * (requires JWT + USER authority).
 */
@RestController
@RequestMapping("/api/seasons/leagues")
public class LeagueController {

    private final LeagueService leagueService;
    private final UserRepository userRepository;

    public LeagueController(LeagueService leagueService, UserRepository userRepository) {
        this.leagueService = leagueService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<LeagueResponse> create(@Valid @RequestBody CreateLeagueRequest body) {
        LeagueResponse resp = leagueService.createLeague(getAuthenticatedUser(), body);
        return new ResponseEntity<>(resp, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<List<LeagueSummary>> myLeagues() {
        return ResponseEntity.ok(leagueService.getMyLeagues(getAuthenticatedUser()));
    }

    @GetMapping("/{code}")
    public ResponseEntity<LeagueDetailResponse> detail(@PathVariable String code) {
        return ResponseEntity.ok(leagueService.getDetail(getAuthenticatedUser(), normaliseCode(code)));
    }

    /**
     * Lightweight preview that does NOT require the caller to be a member —
     * powers the share-link landing page's confirm-before-join dialog so a
     * mistaken click on a deep link does not silently add the user to a
     * league.
     */
    @GetMapping("/{code}/preview")
    public ResponseEntity<LeaguePreviewResponse> preview(@PathVariable String code) {
        return ResponseEntity.ok(leagueService.getPreview(getAuthenticatedUser(), normaliseCode(code)));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<LeagueResponse> join(@PathVariable String code) {
        LeagueResponse resp = leagueService.joinLeague(getAuthenticatedUser(), normaliseCode(code));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{code}/leave")
    public ResponseEntity<ApiResponse> leave(@PathVariable String code) {
        leagueService.leaveLeague(getAuthenticatedUser(), normaliseCode(code));
        ApiResponse ok = new ApiResponse("Left league", true, HttpStatus.OK.value(), HttpStatus.OK);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/{code}/leaderboard")
    public ResponseEntity<LeagueLeaderboardPageResponse> leaderboard(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(leagueService.getLeaderboard(
                getAuthenticatedUser(), normaliseCode(code), page, size));
    }

    private String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("User not found");
        }
        User user = userRepository.findByUsername(auth.getName());
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + auth.getName());
        }
        return user;
    }
}
