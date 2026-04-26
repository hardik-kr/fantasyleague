package com.cricket.fantasyleague.controller.season;

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
import com.cricket.fantasyleague.payload.dto.UserDto;
import com.cricket.fantasyleague.payload.season.UserTransferDto;
import com.cricket.fantasyleague.payload.season.DraftResponse;
import com.cricket.fantasyleague.payload.season.LeaderboardPageResponse;
import com.cricket.fantasyleague.payload.season.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.response.MatchPlayerPointsResponse;
import com.cricket.fantasyleague.payload.season.UserTeamRequest;
import com.cricket.fantasyleague.payload.season.UserTeamResponse;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.season.LeaderboardService;
import com.cricket.fantasyleague.service.api.PlayerPointsQueryService;
import com.cricket.fantasyleague.service.season.UserTeamService;
import com.cricket.fantasyleague.service.season.TransferWorkflowService;

/**
 * Season-long fantasy endpoints.
 *
 * <p>Mirrors the {@code /api/daily/*} convention used by Daily Challenge:
 * everything that is specific to the season-long flow lives under
 * {@code /api/seasons/*}. Cross-cutting endpoints (matches list, current user,
 * generic player listings) remain on {@link CommonController} at {@code /api/*}.
 */
@RestController
@RequestMapping("/api/seasons")
public class SeasonController {

    private final UserTeamService userTeamService;
    private final LeaderboardService leaderboardService;
    private final PlayerPointsQueryService playerPointsQueryService;
    private final TransferWorkflowService transferWorkflowService;
    private final UserRepository userRepository;

    public SeasonController(UserTeamService userTeamService,
                            LeaderboardService leaderboardService,
                            PlayerPointsQueryService playerPointsQueryService,
                            TransferWorkflowService transferWorkflowService,
                            UserRepository userRepository) {
        this.userTeamService = userTeamService;
        this.leaderboardService = leaderboardService;
        this.playerPointsQueryService = playerPointsQueryService;
        this.transferWorkflowService = transferWorkflowService;
        this.userRepository = userRepository;
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
    public ResponseEntity<LeaderboardPageResponse> getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int clampedSize = Math.min(size, 100);
        return ResponseEntity.ok(leaderboardService.getRankedLeaderboard(
                page, clampedSize, getAuthenticatedUser()));
    }

    @GetMapping("/points/{matchId}")
    public ResponseEntity<List<MatchPlayerPointsResponse>> getMatchPlayerPoints(@PathVariable Integer matchId) {
        return ResponseEntity.ok(playerPointsQueryService.getMatchPlayerPoints(matchId));
    }

    @PostMapping("/team")
    public ResponseEntity<UserTeamResponse> getUserTeam(@RequestBody UserTeamRequest body) {
        return ResponseEntity.ok(userTeamService.getUserTeamForMatch(body.userId(), body.matchId()));
    }

    @PostMapping("/transfer-update")
    public ResponseEntity<ApiResponse> transferUpdate(@RequestBody UserTransferDto userTransferDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDto userObj = (UserDto) authentication.getPrincipal();
        transferWorkflowService.makeTransferForCurrentWindow(userTransferDto, userObj.getEmail());
        ApiResponse response = new ApiResponse("success", true, HttpStatus.CREATED.value(), HttpStatus.CREATED);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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
