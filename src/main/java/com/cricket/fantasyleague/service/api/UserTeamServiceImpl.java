package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.payload.response.DraftResponse;
import com.cricket.fantasyleague.payload.response.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.response.PlayerBrief;
import com.cricket.fantasyleague.payload.response.PlayerDetailResponse;
import com.cricket.fantasyleague.payload.response.UserTeamResponse;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.repository.UserMatchStatsDraftRespository;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.match.MatchService;

@Service
public class UserTeamServiceImpl implements UserTeamService {

    private final MatchService matchService;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserMatchStatsDraftRespository userMatchStatsDraftRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final PlayerPointsRepository playerPointsRepository;
    private final UserRepository userRepository;
    private final CricketMasterDataDao dao;
    private final CricketEntityMapper mapper;
    private final Set<Integer> freeTransferMatchIds;

    public UserTeamServiceImpl(MatchService matchService,
                               UserMatchStatsRespository userMatchStatsRepository,
                               UserMatchStatsDraftRespository userMatchStatsDraftRepository,
                               UserOverallStatsRepository userOverallStatsRepository,
                               PlayerPointsRepository playerPointsRepository,
                               UserRepository userRepository,
                               CricketMasterDataDao dao,
                               CricketEntityMapper mapper,
                               @Value("${fantasy.free-transfer-match-ids:}") List<Integer> freeTransferMatchIdList) {
        this.matchService = matchService;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userMatchStatsDraftRepository = userMatchStatsDraftRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.playerPointsRepository = playerPointsRepository;
        this.userRepository = userRepository;
        this.dao = dao;
        this.mapper = mapper;
        this.freeTransferMatchIds = freeTransferMatchIdList != null && !freeTransferMatchIdList.isEmpty()
                ? new HashSet<>(freeTransferMatchIdList) : Collections.emptySet();
    }

    @Override
    public DraftResponse getDraftForNextMatch(User user) {
        Match nextMatch = matchService.findNextUpcomingMatch();
        if (nextMatch == null) {
            return new DraftResponse("No upcoming match found",
                    null, null, null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null);
        }

        String teamA = nextMatch.getTeamA() != null ? nextMatch.getTeamA().getShortName() : null;
        String teamB = nextMatch.getTeamB() != null ? nextMatch.getTeamB().getShortName() : null;

        UserMatchStatsDraft draft = userMatchStatsDraftRepository.findByMatchidAndUserid(nextMatch, user);
        if (draft == null) {
            return new DraftResponse(null,
                    nextMatch.getId(), nextMatch.getDate(), nextMatch.getTime(),
                    nextMatch.getMatchDesc(), teamA, teamB,
                    false, null, null, null, null, null,
                    null, null, null, null, null, null);
        }

        List<PlayerBrief> playing11 = List.of();
        if (draft.getPlaying11() != null) {
            playing11 = new ArrayList<>(draft.getPlaying11().size());
            for (Player p : draft.getPlaying11()) {
                playing11.add(new PlayerBrief(p.getId(), p.getName(), p.getRole()));
            }
        }

        UserOverallStats overall = userOverallStatsRepository.findByUserid(user);
        List<String> usedBoosters = overall != null
                ? overall.getUsedBoosterSet().stream().map(Enum::name).toList()
                : List.of();

        List<Integer> previousPlaying11 = null;
        Match prevMatch = matchService.findPreviousMatch(nextMatch);
        if (prevMatch != null) {
            UserMatchStats prevStats = userMatchStatsRepository.findByMatchidAndUserid(prevMatch, user);
            if (prevStats != null && prevStats.getPlaying11() != null) {
                previousPlaying11 = prevStats.getPlaying11().stream().map(Player::getId).toList();
            }
        }

        return new DraftResponse(null,
                nextMatch.getId(), nextMatch.getDate(), nextMatch.getTime(),
                nextMatch.getMatchDesc(), teamA, teamB,
                true,
                draft.getBoosterused(),
                draft.getTransferused(),
                draft.getCaptainid() != null ? draft.getCaptainid().getId() : null,
                draft.getVicecaptainid() != null ? draft.getVicecaptainid().getId() : null,
                draft.getTripleboosterplayerid() != null ? draft.getTripleboosterplayerid().getId() : null,
                playing11,
                overall != null ? overall.getTransferleft() : 0,
                overall != null ? overall.getBoosterleft() : 0,
                freeTransferMatchIds.contains(nextMatch.getId()),
                usedBoosters,
                previousPlaying11);
    }

    @Override
    public List<MatchHistoryResponse> getMatchHistory(User user) {
        List<UserMatchStats> allStats = userMatchStatsRepository.findByUserid(user);
        List<MatchHistoryResponse> result = new ArrayList<>(allStats.size());

        for (UserMatchStats ums : allStats) {
            Match match = ums.getMatchid();

            String teamA = null;
            String teamB = null;
            if (match != null) {
                teamA = match.getTeamA() != null ? match.getTeamA().getShortName() : null;
                teamB = match.getTeamB() != null ? match.getTeamB().getShortName() : null;
            }

            List<Integer> playerIds = List.of();
            if (ums.getPlaying11() != null) {
                playerIds = new ArrayList<>(ums.getPlaying11().size());
                for (Player p : ums.getPlaying11()) {
                    playerIds.add(p.getId());
                }
            }

            result.add(new MatchHistoryResponse(
                    match != null ? match.getId() : null,
                    match != null ? match.getDate() : null,
                    teamA, teamB,
                    ums.getMatchpoints(),
                    ums.getBoosterused(),
                    ums.getTransferused(),
                    ums.getCaptainid() != null ? ums.getCaptainid().getId() : null,
                    ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null,
                    playerIds
            ));
        }
        return result;
    }

    @Override
    public UserTeamResponse getUserTeamForMatch(Long userId, Integer matchId) {
        if (userId == null || matchId == null) {
            return new UserTeamResponse(false, "userId and matchId are required",
                    null, null, null, null, null, null, null, null, null, null);
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        Match match = dao.findMatchById(matchId).map(mapper::toMatch).orElse(null);
        if (match == null) {
            throw new IllegalArgumentException("Match not found");
        }

        UserMatchStats ums = userMatchStatsRepository.findByMatchidAndUserid(match, user);
        if (ums == null) {
            return new UserTeamResponse(false, "No locked team for this user/match",
                    null, null, null, null, null, null, null, null, null, null);
        }

        Map<Integer, Double> ppMap = new HashMap<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }

        List<PlayerDetailResponse> playing11 = List.of();
        if (ums.getPlaying11() != null) {
            playing11 = new ArrayList<>(ums.getPlaying11().size());
            for (Player p : ums.getPlaying11()) {
                String tag = resolvePlayerTag(p, ums);
                playing11.add(new PlayerDetailResponse(
                        p.getId(), p.getName(),
                        p.getRole() != null ? p.getRole().name() : null,
                        ppMap.getOrDefault(p.getId(), 0.0),
                        tag
                ));
            }
        }

        return new UserTeamResponse(true, null,
                userId, user.getUsername(), user.getFirstname(),
                matchId, ums.getMatchpoints(),
                ums.getBoosterused(), ums.getTransferused(),
                ums.getCaptainid() != null ? ums.getCaptainid().getId() : null,
                ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null,
                playing11);
    }

    private String resolvePlayerTag(Player player, UserMatchStats ums) {
        Integer pid = player.getId();
        if (ums.getCaptainid() != null && pid.equals(ums.getCaptainid().getId())) {
            return "CAPTAIN";
        }
        if (ums.getVicecaptainid() != null && pid.equals(ums.getVicecaptainid().getId())) {
            return "VICE_CAPTAIN";
        }
        if (ums.getTripleboosterplayerid() != null && pid.equals(ums.getTripleboosterplayerid().getId())) {
            return "TRIPLE_SCORER";
        }
        return null;
    }
}
