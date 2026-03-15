package com.cricket.fantasyleague.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.InvalidTeamException;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;

@Service
public class UserTransferServiceImpl implements UserTransferService {

    private static final Logger logger = LoggerFactory.getLogger(UserTransferServiceImpl.class);

    private final UserTransferPersistServiceImpl persistService;
    private final MatchService matchService;
    private final Executor taskExecutor;

    public UserTransferServiceImpl(UserTransferPersistServiceImpl persistService,
                                   MatchService matchService,
                                   @Qualifier("fantasyTaskExecutor") Executor taskExecutor) {
        this.persistService = persistService;
        this.matchService = matchService;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String userEmail) {
        User userObj = persistService.findUserByEmail(userEmail);
        List<Player> newTeamObj = persistService.findPlayersById(userTransferDto.getUserplaying11());
        Match prevMatch = matchService.findPreviousMatch(nextMatch.getMatchnum());

        int substitution = 0;
        if (prevMatch != null) {
            UserMatchStats userMatchStats = persistService.findMatchStatsByMatchAndUser(prevMatch, userObj);
            UserOverallStats userOverallStats = persistService.findOverallStatsByUser(userObj);
            substitution = findCountOfSubstitution(newTeamObj, userMatchStats.getPlaying11(),
                    userOverallStats.getTransferleft());
        }

        Player captainid = validateCaptain(userTransferDto.getCaptainid());
        Player vicecaptain = validateViceCaptain(userTransferDto.getVicecaptainid());
        Player triplebooster_pid = getTriplePlayerId(userTransferDto.getTripleboostpid());

        UserMatchStatsDraft userMatchStatsDraft =
                persistService.findDraftByMatchAndUser(nextMatch, userObj);

        if (userMatchStatsDraft == null) {
            userMatchStatsDraft = new UserMatchStatsDraft(userObj, nextMatch,
                    userTransferDto.getBoosterid(), substitution, 0.0,
                    captainid, vicecaptain, triplebooster_pid, newTeamObj);
        } else {
            userMatchStatsDraft.setBoosterused(userTransferDto.getBoosterid());
            userMatchStatsDraft.setCaptainid(captainid);
            userMatchStatsDraft.setPlaying11(newTeamObj);
            userMatchStatsDraft.setTransferused(substitution);
            userMatchStatsDraft.setTripleboosterplayerid(triplebooster_pid);
            userMatchStatsDraft.setVicecaptainid(vicecaptain);
        }

        persistService.saveDraft(userMatchStatsDraft);
    }

    private Integer findCountOfSubstitution(List<Player> newTeam, List<Player> oldTeam,
                                            Integer currSubsLeft) {
        HashSet<Player> hashset = new HashSet<>(oldTeam);
        int substitution = 0;
        for (Player newPlayerObj : newTeam) {
            if (!hashset.contains(newPlayerObj)) {
                substitution++;
            }
        }

        if (currSubsLeft - substitution < 0) {
            throw new InvalidTeamException(
                    String.format("No substitution left, max sub left : %d", currSubsLeft));
        }

        return substitution;
    }

    private Player getTriplePlayerId(Integer tripleboostpid) {
        if (tripleboostpid == null) return null;

        Player triple_pid = persistService.getPlayerReference(tripleboostpid);
        if (triple_pid == null) {
            throw new ResourceNotFoundException("Invalid triple booster player in playing11");
        }
        return triple_pid;
    }

    private Player validateViceCaptain(Integer vicecaptainid) {
        Player vicecaptain = persistService.getPlayerReference(vicecaptainid);
        if (vicecaptain == null) {
            throw new ResourceNotFoundException("Invalid vicecaptain in playing11");
        }
        return vicecaptain;
    }

    private Player validateCaptain(Integer captainid) {
        Player captain = persistService.getPlayerReference(captainid);
        if (captain == null) {
            throw new ResourceNotFoundException("Invalid captain in playing11");
        }
        return captain;
    }

    @SuppressWarnings("unused")
    private void validateTeam(List<Player> newTeamObj) {
        double credit = 0.0;
        if (newTeamObj.size() != 11) {
            throw new ResourceNotFoundException("Invalid player in playing11");
        }

        for (Player pobj : newTeamObj) {
            credit += pobj.getCredit();
        }
        if (credit > 100) {
            throw new InvalidTeamException("Credit limit exceeded, Max limit:100");
        }

        int overseasCount = 0;
        for (Player pobj : newTeamObj) {
            if (Boolean.TRUE.equals(pobj.getOverseas())) {
                overseasCount++;
            }
            if (overseasCount > 4) {
                throw new InvalidTeamException("Overseas limit exceeded, Max limit:4");
            }
        }

        HashMap<Team, Integer> playerMap = new HashMap<>();
        for (Player pobj : newTeamObj) {
            Team teamid = pobj.getTeamid();
            if (playerMap.containsKey(teamid)) {
                int currCount = playerMap.get(teamid);
                if (currCount == 7) {
                    throw new InvalidTeamException(
                            "Player limit for single team limit exceeded, Max limit:7");
                } else {
                    playerMap.put(teamid, currCount + 1);
                }
            } else {
                playerMap.put(teamid, 1);
            }
        }
    }

    @Override
    public void lockMatchTeam(Match currMatch) {
        List<User> allUser = persistService.findAllUsers();

        CompletableFuture<?>[] futures = allUser.stream()
                .map(user -> CompletableFuture.runAsync(
                        () -> lockSingleUserTeam(user, currMatch), taskExecutor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
    }

    private void lockSingleUserTeam(User user, Match currMatch) {
        if ("admin@gmail.com".equals(user.getEmail())) return;

        try {
            UserMatchStatsDraft userMatchDraft =
                    persistService.findDraftByMatchAndUser(currMatch, user);
            if (userMatchDraft == null) {
                logger.warn("No draft team found for user {} match {}", user.getEmail(), currMatch.getId());
                return;
            }

            UserMatchStats userMatchStats = new UserMatchStats(user, currMatch,
                    userMatchDraft.getBoosterused(), userMatchDraft.getTransferused(),
                    userMatchDraft.getMatchpoints(), userMatchDraft.getCaptainid(),
                    userMatchDraft.getVicecaptainid(), userMatchDraft.getTripleboosterplayerid(),
                    userMatchDraft.getPlaying11());

            UserOverallStats userOverall = persistService.findOverallStatsByUser(user);
            userOverall.setPrevpoints(userOverall.getTotalpoints());

            persistService.saveOverallStats(userOverall);
            persistService.saveMatchStats(userMatchStats);
        } catch (Exception e) {
            logger.error("Failed to lock team for user {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }
}
