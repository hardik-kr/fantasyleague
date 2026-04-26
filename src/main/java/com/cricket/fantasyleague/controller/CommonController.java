package com.cricket.fantasyleague.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.PlayerMatchPointsResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.api.FantasyMatchService;
import com.cricket.fantasyleague.service.api.FantasyPlayerService;
import com.cricket.fantasyleague.service.api.PlayerStatsQueryService;
import com.cricket.fantasyleague.service.api.UserProfileService;

/**
 * Cross-cutting endpoints used by both Season Long and Daily Challenge.
 *
 * <p>Lives at {@code /api/*} because the data is mode-agnostic — the underlying
 * match list, player catalog and authenticated user profile are shared. Mode-
 * specific reads/writes go to {@link SeasonController} ({@code /api/seasons})
 * or {@code com.cricket.fantasyleague.controller.daily.DailyChallengeController}
 * ({@code /api/daily}).
 */
@RestController
@RequestMapping("/api")
public class CommonController {

    private final FantasyMatchService fantasyMatchService;
    private final FantasyPlayerService fantasyPlayerService;
    private final UserProfileService userProfileService;
    private final PlayerStatsQueryService playerStatsQueryService;
    private final UserRepository userRepository;

    public CommonController(FantasyMatchService fantasyMatchService,
                            FantasyPlayerService fantasyPlayerService,
                            UserProfileService userProfileService,
                            PlayerStatsQueryService playerStatsQueryService,
                            UserRepository userRepository) {
        this.fantasyMatchService = fantasyMatchService;
        this.fantasyPlayerService = fantasyPlayerService;
        this.userProfileService = userProfileService;
        this.playerStatsQueryService = playerStatsQueryService;
        this.userRepository = userRepository;
    }

    @GetMapping("/matches")
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        return ResponseEntity.ok(fantasyMatchService.getAllMatchesWithTeams());
    }

    @GetMapping("/players")
    public ResponseEntity<List<PlayerResponse>> getAllPlayers(@RequestParam Integer leagueId) {
        return ResponseEntity.ok(fantasyPlayerService.getAllPlayersWithConfig(leagueId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userProfileService.getProfile(getAuthenticatedUser()));
    }

    @GetMapping("/players/{playerId}/points")
    public ResponseEntity<List<PlayerMatchPointsResponse>> getPlayerPointsHistory(@PathVariable Integer playerId) {
        return ResponseEntity.ok(playerStatsQueryService.getPointsHistory(playerId));
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + username);
        }
        return user;
    }
}
