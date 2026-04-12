package com.cricket.fantasyleague.controller;

import java.util.List;

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
import com.cricket.fantasyleague.payload.response.DraftResponse;
import com.cricket.fantasyleague.payload.response.LeaderboardEntry;
import com.cricket.fantasyleague.payload.response.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.response.MatchPlayerPointsResponse;
import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;
import com.cricket.fantasyleague.payload.response.UserTeamRequest;
import com.cricket.fantasyleague.payload.response.UserTeamResponse;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.api.FantasyMatchService;
import com.cricket.fantasyleague.service.api.FantasyPlayerService;
import com.cricket.fantasyleague.service.api.LeaderboardService;
import com.cricket.fantasyleague.service.api.PlayerPointsQueryService;
import com.cricket.fantasyleague.service.api.UserProfileService;
import com.cricket.fantasyleague.service.api.UserTeamService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final FantasyMatchService fantasyMatchService;
    private final FantasyPlayerService fantasyPlayerService;
    private final UserProfileService userProfileService;
    private final UserTeamService userTeamService;
    private final LeaderboardService leaderboardService;
    private final PlayerPointsQueryService playerPointsQueryService;
    private final UserRepository userRepository;

    public ApiController(FantasyMatchService fantasyMatchService,
                         FantasyPlayerService fantasyPlayerService,
                         UserProfileService userProfileService,
                         UserTeamService userTeamService,
                         LeaderboardService leaderboardService,
                         PlayerPointsQueryService playerPointsQueryService,
                         UserRepository userRepository) {
        this.fantasyMatchService = fantasyMatchService;
        this.fantasyPlayerService = fantasyPlayerService;
        this.userProfileService = userProfileService;
        this.userTeamService = userTeamService;
        this.leaderboardService = leaderboardService;
        this.playerPointsQueryService = playerPointsQueryService;
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

    @GetMapping("/me/draft")
    public ResponseEntity<DraftResponse> getMyDraft() {
        return ResponseEntity.ok(userTeamService.getDraftForNextMatch(getAuthenticatedUser()));
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<MatchHistoryResponse>> getMyHistory() {
        return ResponseEntity.ok(userTeamService.getMatchHistory(getAuthenticatedUser()));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getRankedLeaderboard());
    }

    @GetMapping("/points/{matchId}")
    public ResponseEntity<List<MatchPlayerPointsResponse>> getMatchPlayerPoints(@PathVariable Integer matchId) {
        return ResponseEntity.ok(playerPointsQueryService.getMatchPlayerPoints(matchId));
    }

    @PostMapping("/team")
    public ResponseEntity<UserTeamResponse> getUserTeam(@RequestBody UserTeamRequest body) {
        return ResponseEntity.ok(userTeamService.getUserTeamForMatch(body.userId(), body.matchId()));
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
