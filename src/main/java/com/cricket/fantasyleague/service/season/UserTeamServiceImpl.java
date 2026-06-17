package com.cricket.fantasyleague.service.season;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.UserMatchStats;
import com.cricket.fantasyleague.entity.table.season.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.payload.season.DraftResponse;
import com.cricket.fantasyleague.payload.season.MatchHistoryResponse;
import com.cricket.fantasyleague.payload.season.MyTeamPlayer;
import com.cricket.fantasyleague.payload.season.MyTeamResponse;
import com.cricket.fantasyleague.payload.response.PlayerBrief;
import com.cricket.fantasyleague.payload.response.PlayerDetailResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.season.UserTeamResponse;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.service.masterdata.MasterDataReadService;
import com.cricket.fantasyleague.repository.season.UserMatchStatsDraftRespository;
import com.cricket.fantasyleague.repository.season.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.season.UserOverallStatsRepository;
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
    private final MasterDataReadService masterDataReadService;
    private final LiveMatchCache liveMatchCache;
    private final LiveMatchUserCache liveMatchUserCache;
    private final Set<Integer> freeTransferMatchIds;

    private static final EnumSet<MatchState> LIVE_MATCH_STATES =
            EnumSet.of(MatchState.IN_PROGRESS, MatchState.DELAY);

    public UserTeamServiceImpl(MatchService matchService,
                               UserMatchStatsRespository userMatchStatsRepository,
                               UserMatchStatsDraftRespository userMatchStatsDraftRepository,
                               UserOverallStatsRepository userOverallStatsRepository,
                               PlayerPointsRepository playerPointsRepository,
                               UserRepository userRepository,
                               CricketMasterDataDao dao,
                               CricketEntityMapper mapper,
                               MasterDataReadService masterDataReadService,
                               LiveMatchCache liveMatchCache,
                               LiveMatchUserCache liveMatchUserCache,
                               @Value("${fantasy.free-transfer-match-ids:}") String freeTransferMatchIdsCsv) {
        this.matchService = matchService;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userMatchStatsDraftRepository = userMatchStatsDraftRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.playerPointsRepository = playerPointsRepository;
        this.userRepository = userRepository;
        this.dao = dao;
        this.mapper = mapper;
        this.masterDataReadService = masterDataReadService;
        this.liveMatchCache = liveMatchCache;
        this.liveMatchUserCache = liveMatchUserCache;
        this.freeTransferMatchIds = new HashSet<>();
        if (freeTransferMatchIdsCsv != null && !freeTransferMatchIdsCsv.isBlank()) {
            for (String part : freeTransferMatchIdsCsv.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    this.freeTransferMatchIds.add(Integer.valueOf(id));
                }
            }
        }
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
                    null, null, null, freeTransferMatchIds.contains(nextMatch.getId()), null, null);
        }

        List<PlayerBrief> playing11 = buildPlayerBriefs(draft.getPlaying11(), nextMatch.getLeagueId());

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
    public MyTeamResponse getMyTeamForPreview(User user) {
        Match liveMatch = matchService.findActiveLiveMatch();
        if (liveMatch != null) {
            UserMatchStats locked = userMatchStatsRepository.findByMatchidAndUserid(liveMatch, user);
            if (locked != null) {
                return myTeamFromLockedMatch(liveMatch, locked);
            }
        }
        return myTeamFromUpcomingMatch(user);
    }

    private MyTeamResponse myTeamFromLockedMatch(Match match, UserMatchStats ums) {
        String teamA = match.getTeamA() != null ? match.getTeamA().getShortName() : null;
        String teamB = match.getTeamB() != null ? match.getTeamB().getShortName() : null;
        Map<Integer, Double> ppMap = buildPlayerPointsMap(match.getId());
        Double matchPoints = resolveMatchPoints(match, ums);

        return new MyTeamResponse(
                null,
                matchStateName(match),
                match.getMatchDesc(),
                match.getDate(),
                teamA,
                teamB,
                ums.getBoosterused(),
                ums.getCaptainid() != null ? ums.getCaptainid().getId() : null,
                ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null,
                ums.getTripleboosterplayerid() != null ? ums.getTripleboosterplayerid().getId() : null,
                matchPoints,
                buildMyTeamPlayers(ums.getPlaying11(), match.getLeagueId(), ppMap));
    }

    private MyTeamResponse myTeamFromUpcomingMatch(User user) {
        Match nextMatch = matchService.findNextUpcomingMatch();
        if (nextMatch == null) {
            return new MyTeamResponse(
                    "No upcoming match found",
                    null, null, null, null, null,
                    null, null, null, null, null,
                    List.of());
        }

        String teamA = nextMatch.getTeamA() != null ? nextMatch.getTeamA().getShortName() : null;
        String teamB = nextMatch.getTeamB() != null ? nextMatch.getTeamB().getShortName() : null;
        UserMatchStatsDraft draft = userMatchStatsDraftRepository.findByMatchidAndUserid(nextMatch, user);
        if (draft == null || draft.getPlaying11() == null || draft.getPlaying11().isEmpty()) {
            return new MyTeamResponse(
                    null,
                    matchStateName(nextMatch),
                    nextMatch.getMatchDesc(),
                    nextMatch.getDate(),
                    teamA,
                    teamB,
                    null,
                    null, null, null, null,
                    List.of());
        }

        return new MyTeamResponse(
                null,
                matchStateName(nextMatch),
                nextMatch.getMatchDesc(),
                nextMatch.getDate(),
                teamA,
                teamB,
                draft.getBoosterused(),
                draft.getCaptainid() != null ? draft.getCaptainid().getId() : null,
                draft.getVicecaptainid() != null ? draft.getVicecaptainid().getId() : null,
                draft.getTripleboosterplayerid() != null ? draft.getTripleboosterplayerid().getId() : null,
                null,
                buildMyTeamPlayers(draft.getPlaying11(), nextMatch.getLeagueId(), null));
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
                    userId, user.getUsername(), user.getFirstname(), matchId, 0.0, null, 0, null, null, List.of());
        }

        Map<Integer, Double> ppMap = buildPlayerPointsMap(matchId);
        Map<Integer, String> teamMap = buildPlayerTeamMapFromMasterCache(match.getLeagueId(), ums.getPlaying11());

        List<PlayerDetailResponse> playing11 = List.of();
        if (ums.getPlaying11() != null) {
            playing11 = new ArrayList<>(ums.getPlaying11().size());
            for (Player p : ums.getPlaying11()) {
                String tag = resolvePlayerTag(p, ums);
                playing11.add(new PlayerDetailResponse(
                        p.getId(), p.getName(),
                        p.getRole() != null ? p.getRole().name() : null,
                        ppMap.getOrDefault(p.getId(), 0.0),
                        tag,
                        teamMap.get(p.getId())
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

    private String matchStateName(Match match) {
        return match != null && match.getMatchState() != null ? match.getMatchState().name() : null;
    }

    private Map<Integer, String> buildPlayerTeamMapFromMasterCache(Integer leagueId, List<Player> players) {
        if (leagueId == null || players == null || players.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> map = new HashMap<>(players.size());
        for (Player p : players) {
            if (p == null || p.getId() == null) {
                continue;
            }
            masterDataReadService.getPlayerWithConfig(leagueId, p.getId())
                    .map(PlayerResponse::teamShortName)
                    .ifPresent(shortName -> map.put(p.getId(), shortName));
        }
        return map;
    }

    private Map<Integer, Double> buildPlayerPointsMap(Integer matchId) {
        Map<Integer, Double> ppMap = new HashMap<>();
        if (matchId == null) {
            return ppMap;
        }
        List<PlayerPoints> cached = liveMatchCache.getPlayerPointsRecords(matchId);
        if (cached != null && !cached.isEmpty()) {
            for (PlayerPoints pp : cached) {
                if (pp.getPlayerId() != null) {
                    ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
                }
            }
            return ppMap;
        }
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }
        return ppMap;
    }

    private Double resolveMatchPoints(Match match, UserMatchStats ums) {
        if (match == null || ums == null || ums.getUserid() == null) {
            return ums != null ? ums.getMatchpoints() : null;
        }
        if (match.getMatchState() != null && LIVE_MATCH_STATES.contains(match.getMatchState())) {
            Double cached = liveMatchUserCache.getUserMatchPoints(match.getId(), ums.getUserid().getId());
            if (cached != null) {
                return cached;
            }
        }
        return ums.getMatchpoints();
    }

    private List<PlayerBrief> buildPlayerBriefs(List<Player> players, Integer leagueId) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> teamMap = buildPlayerTeamMapFromMasterCache(leagueId, players);
        List<PlayerBrief> briefs = new ArrayList<>(players.size());
        for (Player p : players) {
            briefs.add(new PlayerBrief(p.getId(), p.getName(), p.getRole(), teamMap.get(p.getId())));
        }
        return briefs;
    }

    private List<MyTeamPlayer> buildMyTeamPlayers(List<Player> players, Integer leagueId,
                                                  Map<Integer, Double> pointsById) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> teamMap = buildPlayerTeamMapFromMasterCache(leagueId, players);
        List<MyTeamPlayer> rows = new ArrayList<>(players.size());
        for (Player p : players) {
            Double pts = pointsById != null ? pointsById.getOrDefault(p.getId(), 0.0) : null;
            rows.add(new MyTeamPlayer(p.getId(), p.getName(), p.getRole(), teamMap.get(p.getId()), pts));
        }
        return rows;
    }
}
