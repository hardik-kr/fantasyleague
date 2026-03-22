package com.cricket.fantasyleague.service.usertransfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerTeamData;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.InvalidTeamException;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.dto.UserTransferDto;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.service.match.MatchService;

@Service
public class UserTransferServiceImpl implements UserTransferService {

    private static final Logger logger = LoggerFactory.getLogger(UserTransferServiceImpl.class);

    private final UserTransferPersistServiceImpl persistService;
    private final MatchService matchService;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final CricketMasterDataDao cricketDao;

    public UserTransferServiceImpl(UserTransferPersistServiceImpl persistService,
                                   MatchService matchService,
                                   FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                                   CricketMasterDataDao cricketDao) {
        this.persistService = persistService;
        this.matchService = matchService;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.cricketDao = cricketDao;
    }

    @Override
    public void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String userEmail) {
        validateDraftInput(userTransferDto, nextMatch.getLeagueId());

        User userObj = persistService.findUserByEmail(userEmail);
        List<Player> newTeamObj = persistService.findPlayersById(userTransferDto.getUserplaying11());
        Match prevMatch = matchService.findPreviousMatch(nextMatch);

        int substitution = 0;
        boolean isSuperTransfer = userTransferDto.getBoosterid() == Booster.SUPER_TRANSFER;
        if (!isSuperTransfer && prevMatch != null) {
            UserMatchStats userMatchStats = persistService.findMatchStatsByMatchAndUser(prevMatch, userObj);
            UserOverallStats userOverallStats = persistService.findOverallStatsByUser(userObj);
            if (userMatchStats != null && userOverallStats != null) {
                substitution = findCountOfSubstitution(newTeamObj, userMatchStats.getPlaying11(),
                        userOverallStats.getTransferleft());
            }
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
            userMatchStatsDraft.getPlaying11().clear();
            userMatchStatsDraft.getPlaying11().addAll(newTeamObj);
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

    private void validateDraftInput(UserTransferDto dto, Integer leagueId) {
        List<Integer> ids = dto.getUserplaying11();

        if (ids == null || ids.size() != 11) {
            throw new InvalidTeamException("Playing XI must contain exactly 11 players");
        }

        HashSet<Integer> idSet = new HashSet<>(ids);
        if (idSet.size() != 11) {
            throw new InvalidTeamException("Playing XI contains duplicate players");
        }

        if (dto.getCaptainid() == null || !idSet.contains(dto.getCaptainid())) {
            throw new InvalidTeamException("Captain must be a player in the playing XI");
        }
        if (dto.getVicecaptainid() == null || !idSet.contains(dto.getVicecaptainid())) {
            throw new InvalidTeamException("Vice-captain must be a player in the playing XI");
        }
        if (dto.getCaptainid().equals(dto.getVicecaptainid())) {
            throw new InvalidTeamException("Captain and vice-captain must be different players");
        }
        if (dto.getBoosterid() == Booster.TRIPLE_SCORER) {
            if (dto.getTripleboostpid() == null || !idSet.contains(dto.getTripleboostpid())) {
                throw new InvalidTeamException("Triple scorer player must be in the playing XI");
            }
        }

        List<Player> players = persistService.findPlayersById(ids);
        if (players.size() != 11) {
            throw new InvalidTeamException("One or more player IDs are invalid");
        }

        Map<Integer, FantasyPlayerConfig> configMap = buildConfigMap(leagueId);

        int batters = 0, bowlers = 0, keepers = 0, allrounders = 0;
        int overseasCount = 0;
        Map<Integer, Integer> teamCount = new HashMap<>();

        for (Player p : players) {
            PlayerType role = p.getRole();
            if (role != null) {
                switch (role) {
                    case BATTER -> batters++;
                    case BOWLER -> bowlers++;
                    case KEEPER -> keepers++;
                    case ALLROUNDER -> allrounders++;
                }
            }

            FantasyPlayerConfig cfg = configMap.get(p.getId());
            if (cfg != null && Boolean.TRUE.equals(cfg.getOverseas())) {
                overseasCount++;
            }

            List<PlayerTeamData> playerTeams = cricketDao.findTeamsByPlayerId(p.getId());
            for (PlayerTeamData ptd : playerTeams) {
                if (Boolean.TRUE.equals(ptd.isActive())) {
                    teamCount.merge(ptd.teamId(), 1, Integer::sum);
                }
            }
        }

        if (batters < 3 || batters > 6) {
            throw new InvalidTeamException("Batters must be between 3 and 6, found: " + batters);
        }
        if (bowlers < 3 || bowlers > 6) {
            throw new InvalidTeamException("Bowlers must be between 3 and 6, found: " + bowlers);
        }
        if (keepers < 1 || keepers > 4) {
            throw new InvalidTeamException("Keepers must be between 1 and 4, found: " + keepers);
        }
        if (allrounders < 1 || allrounders > 4) {
            throw new InvalidTeamException("Allrounders must be between 1 and 4, found: " + allrounders);
        }
        if (overseasCount > 4) {
            throw new InvalidTeamException("Max 4 overseas players allowed, found: " + overseasCount);
        }
        for (Map.Entry<Integer, Integer> entry : teamCount.entrySet()) {
            if (entry.getValue() > 7) {
                throw new InvalidTeamException("Max 7 players from one team allowed, teamId=" + entry.getKey() + " has " + entry.getValue());
            }
        }
    }

    private Map<Integer, FantasyPlayerConfig> buildConfigMap(Integer leagueId) {
        if (leagueId == null) return Map.of();
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);
        Map<Integer, FantasyPlayerConfig> map = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            map.put(cfg.getPlayerId(), cfg);
        }
        return map;
    }

    @Override
    @Transactional
    public void lockMatchTeam(Match currMatch) {
        List<UserMatchStatsDraft> allDrafts = persistService.findAllDraftsByMatch(currMatch);
        if (allDrafts.isEmpty()) {
            logger.warn("No drafts found for matchId={}", currMatch.getId());
            return;
        }

        List<UserMatchStats> matchStatsBatch = new ArrayList<>(allDrafts.size());
        List<UserOverallStats> overallBatch = new ArrayList<>(allDrafts.size());

        for (UserMatchStatsDraft draft : allDrafts) {
            User user = draft.getUserid();
            if ("admin@gmail.com".equals(user.getEmail())) continue;

            matchStatsBatch.add(new UserMatchStats(user, currMatch,
                    draft.getBoosterused(), draft.getTransferused(),
                    0.0, draft.getCaptainid(), draft.getVicecaptainid(),
                    draft.getTripleboosterplayerid(), draft.getPlaying11()));

            UserOverallStats overall = persistService.findOverallStatsByUser(user);
            if (overall != null) {
                overall.setPrevpoints(overall.getTotalpoints());
                if (draft.getTransferused() != null && draft.getTransferused() > 0) {
                    int newTransferLeft = overall.getTransferleft() != null
                            ? overall.getTransferleft() - draft.getTransferused()
                            : 0;
                    overall.setTransferleft(Math.max(newTransferLeft, 0));
                }
                if (draft.getBoosterused() != null) {
                    int newBoosterLeft = overall.getBoosterleft() != null
                            ? overall.getBoosterleft() - 1
                            : 0;
                    overall.setBoosterleft(Math.max(newBoosterLeft, 0));
                }
                overallBatch.add(overall);
            }
        }

        persistService.saveAllMatchStats(matchStatsBatch);
        persistService.saveAllOverallStats(overallBatch);
        logger.info("Locked {} user teams for matchId={}", matchStatsBatch.size(), currMatch.getId());
    }
}
