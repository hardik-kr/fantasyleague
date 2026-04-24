package com.cricket.fantasyleague.service.usertransfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
    private final Set<Integer> freeTransferMatchIds;
    private final int lockBatchSize;
    private final long lockBatchDelayMs;

    public UserTransferServiceImpl(UserTransferPersistServiceImpl persistService,
                                   MatchService matchService,
                                   FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                                   CricketMasterDataDao cricketDao,
                                   @Value("${fantasy.free-transfer-match-ids:}") List<Integer> freeTransferMatchIdList,
                                   @Value("${fantasy.lock.batch-size:5000}") int lockBatchSize,
                                   @Value("${fantasy.lock.batch-delay-ms:1000}") long lockBatchDelayMs) {
        this.persistService = persistService;
        this.matchService = matchService;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.cricketDao = cricketDao;
        this.freeTransferMatchIds = freeTransferMatchIdList != null && !freeTransferMatchIdList.isEmpty()
                ? new HashSet<>(freeTransferMatchIdList) : Collections.emptySet();
        this.lockBatchSize = lockBatchSize;
        this.lockBatchDelayMs = lockBatchDelayMs;
        logger.info("Free transfer match IDs: {}", this.freeTransferMatchIds);
        logger.info("Lock batch config: size={}, delay={}ms", lockBatchSize, lockBatchDelayMs);
    }

    @Override
    public void makeTransfer(Match nextMatch, UserTransferDto userTransferDto, String userEmail) {
        validateDraftInput(userTransferDto, nextMatch.getLeagueId());

        User userObj = persistService.findUserByEmail(userEmail);
        List<Player> newTeamObj = persistService.findPlayersById(userTransferDto.getUserplaying11());
        Match prevMatch = matchService.findPreviousMatch(nextMatch);

        int substitution = 0;
        boolean isFreeTransferWindow = freeTransferMatchIds.contains(nextMatch.getId());
        if (isFreeTransferWindow && userTransferDto.getBoosterid() == Booster.SUPER_TRANSFER) {
            throw new InvalidTeamException("SUPER_TRANSFER booster cannot be used during a free transfer window");
        }
        if (userTransferDto.getBoosterid() != null) {
            UserOverallStats overallStats = persistService.findOverallStatsByUser(userObj);
            int boostersLeft = overallStats != null && overallStats.getBoosterleft() != null
                    ? overallStats.getBoosterleft() : 0;
            if (boostersLeft <= 0) {
                throw new InvalidTeamException("No boosters remaining for this season");
            }
            if (overallStats != null && overallStats.getUsedBoosterSet().contains(userTransferDto.getBoosterid())) {
                throw new InvalidTeamException(
                        "Booster " + userTransferDto.getBoosterid() + " has already been used in a previous match");
            }
        }
        boolean isSuperTransfer = userTransferDto.getBoosterid() == Booster.SUPER_TRANSFER;
        if (!isFreeTransferWindow && !isSuperTransfer && prevMatch != null) {
            UserMatchStats userMatchStats = persistService.findMatchStatsByMatchAndUser(prevMatch, userObj);
            UserOverallStats userOverallStats = persistService.findOverallStatsByUser(userObj);
            if (userMatchStats != null && userOverallStats != null) {
                substitution = findCountOfSubstitution(newTeamObj, userMatchStats.getPlaying11(),
                        userOverallStats.getTransferleft());
            }
        }
        if (isFreeTransferWindow) {
            logger.info("Free transfer window active for matchId={}, userId={}", nextMatch.getId(), userObj.getId());
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
        double totalCredit = 0.0;
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
            if (cfg != null) {
                if (Boolean.TRUE.equals(cfg.getOverseas())) {
                    overseasCount++;
                }
                if (cfg.getCredit() != null) {
                    totalCredit += cfg.getCredit();
                }
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
        if (totalCredit > 100.0) {
            throw new InvalidTeamException(
                    String.format("Total credit must not exceed 100, current total: %.1f", totalCredit));
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
    public void lockMatchTeam(Match currMatch) {
        long totalDrafts = persistService.countDraftsByMatch(currMatch);
        if (totalDrafts == 0) {
            logger.warn("No drafts found for matchId={}", currMatch.getId());
            return;
        }

        boolean freeWindow = freeTransferMatchIds.contains(currMatch.getId());
        int totalPages = (int) Math.ceil((double) totalDrafts / lockBatchSize);
        int totalLocked = 0;

        logger.info("lockMatchTeam: matchId={}, totalDrafts={}, batchSize={}, pages={}, delay={}ms",
                currMatch.getId(), totalDrafts, lockBatchSize, totalPages, lockBatchDelayMs);

        Match nextMatch = matchService.findUpcomingMatch(currMatch.getDate(), currMatch.getTime());

        for (int page = 0; page < totalPages; page++) {
            List<UserMatchStatsDraft> batch = persistService.findDraftPage(
                    currMatch, PageRequest.of(0, lockBatchSize));

            if (batch.isEmpty()) break;

            List<User> users = batch.stream()
                    .map(UserMatchStatsDraft::getUserid)
                    .filter(u -> !"admin@gmail.com".equals(u.getEmail()))
                    .toList();

            Map<Long, UserOverallStats> overallMap = persistService.findOverallStatsForUsers(users)
                    .stream()
                    .collect(Collectors.toMap(o -> o.getUserid().getId(), Function.identity()));

            // Per-row idempotency (EC-11): if a prior attempt crashed after lockBatch
            // committed but before drafts were cleaned up, the same users would be
            // revisited on resume. Without this check we'd insert duplicate
            // UserMatchStats rows and double-decrement booster/transfer counters.
            List<Long> batchUserIds = users.stream().map(User::getId).toList();
            Set<Long> alreadyLockedUserIds = persistService
                    .findAlreadyLockedUserIds(currMatch, batchUserIds);

            List<UserMatchStats> matchStatsList = new ArrayList<>(batch.size());
            List<UserOverallStats> overallUpdates = new ArrayList<>(batch.size());
            List<UserMatchStatsDraft> draftUpdates = new ArrayList<>(batch.size());
            int skippedAlreadyLocked = 0;

            for (UserMatchStatsDraft draft : batch) {
                User user = draft.getUserid();
                if ("admin@gmail.com".equals(user.getEmail())) {
                    draftUpdates.add(draft);
                    continue;
                }

                if (alreadyLockedUserIds.contains(user.getId())) {
                    // Already inserted in a previous attempt: do NOT insert again and do
                    // NOT decrement booster/transfer (those were already applied earlier).
                    // Still forward/delete the draft so it's consumed exactly once.
                    draftUpdates.add(draft);
                    skippedAlreadyLocked++;
                    continue;
                }

                matchStatsList.add(new UserMatchStats(user, currMatch,
                        draft.getBoosterused(), draft.getTransferused(),
                        0.0, draft.getCaptainid(), draft.getVicecaptainid(),
                        draft.getTripleboosterplayerid(), draft.getPlaying11()));

                UserOverallStats overall = overallMap.get(user.getId());
                if (overall != null) {
                    // Note: totalpoints/prevpoints are NOT touched here.
                    // totalpoints is owned solely by the live-match pipeline
                    // (UserOverallPtsServiceImpl) and is re-derived every tick as
                    // committedTotal + SUM(live_matchpoints). Writing prevpoints
                    // here was the primary source of the historical drift bug.
                    if (!freeWindow && draft.getTransferused() != null && draft.getTransferused() > 0) {
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
                        overall.addUsedBooster(draft.getBoosterused());
                    }
                    overallUpdates.add(overall);
                }

                draftUpdates.add(draft);
            }

            if (skippedAlreadyLocked > 0) {
                logger.info("lockMatchTeam: matchId={}, page {}/{}, resumed — skipped {} already-locked user(s)",
                        currMatch.getId(), page + 1, totalPages, skippedAlreadyLocked);
            }

            persistService.lockBatch(matchStatsList, overallUpdates);
            totalLocked += matchStatsList.size();

            if (nextMatch != null) {
                for (UserMatchStatsDraft draft : draftUpdates) {
                    draft.setMatchid(nextMatch);
                    draft.setTransferused(0);
                    draft.setBoosterused(null);
                }
                persistService.saveAllDrafts(draftUpdates);
            } else {
                persistService.deleteAllDrafts(draftUpdates);
            }

            logger.info("lockMatchTeam: matchId={}, page {}/{}, batchLocked={}, totalLocked={}",
                    currMatch.getId(), page + 1, totalPages, matchStatsList.size(), totalLocked);

            if (page < totalPages - 1 && lockBatchDelayMs > 0) {
                try {
                    Thread.sleep(lockBatchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("lockMatchTeam interrupted at page {}/{}", page + 1, totalPages);
                    break;
                }
            }
        }

        if (nextMatch != null) {
            logger.info("lockMatchTeam complete: matchId={}, locked={}, drafts carried forward to matchId={}",
                    currMatch.getId(), totalLocked, nextMatch.getId());
        } else {
            logger.info("lockMatchTeam complete: matchId={}, locked={}, no next match — drafts deleted",
                    currMatch.getId(), totalLocked);
        }
    }
}
