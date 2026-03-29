package com.cricket.fantasyleague.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.repository.UserMatchStatsDraftRespository;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.match.MatchService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CricketMasterDataDao dao;
    private final CricketEntityMapper mapper;
    private final MatchService matchService;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final PlayerPointsRepository playerPointsRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserMatchStatsDraftRespository userMatchStatsDraftRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserRepository userRepository;
    private final Set<Integer> freeTransferMatchIds;

    public ApiController(CricketMasterDataDao dao,
                         CricketEntityMapper mapper,
                         MatchService matchService,
                         FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                         PlayerPointsRepository playerPointsRepository,
                         UserMatchStatsRespository userMatchStatsRepository,
                         UserMatchStatsDraftRespository userMatchStatsDraftRepository,
                         UserOverallStatsRepository userOverallStatsRepository,
                         UserRepository userRepository,
                         @Value("${fantasy.free-transfer-match-ids:}") List<Integer> freeTransferMatchIdList) {
        this.dao = dao;
        this.mapper = mapper;
        this.matchService = matchService;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.playerPointsRepository = playerPointsRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userMatchStatsDraftRepository = userMatchStatsDraftRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userRepository = userRepository;
        this.freeTransferMatchIds = freeTransferMatchIdList != null && !freeTransferMatchIdList.isEmpty()
                ? new HashSet<>(freeTransferMatchIdList) : Collections.emptySet();
    }

    // ── 1. GET /api/matches — All matches (bulk) ──

    @GetMapping("/matches")
    public ResponseEntity<List<Map<String, Object>>> getAllMatches() {
        List<MatchData> allMatches = dao.findAllMatches();

        Map<Integer, Map<String, Object>> teamCache = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>(allMatches.size());

        for (MatchData md : allMatches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", md.id());
            row.put("date", md.date());
            row.put("time", md.time());
            row.put("venue", md.venue());
            row.put("toss", md.toss());
            row.put("result", md.result());
            row.put("isMatchComplete", md.isMatchComplete());
            row.put("matchState", md.matchState());
            row.put("matchDesc", md.matchDesc());
            row.put("teamA", resolveTeam(md.teamAId(), teamCache));
            row.put("teamB", resolveTeam(md.teamBId(), teamCache));
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> resolveTeam(Integer teamId, Map<Integer, Map<String, Object>> cache) {
        if (teamId == null) return null;
        return cache.computeIfAbsent(teamId, id -> {
            Team t = dao.findTeamById(id).map(mapper::toTeam).orElse(null);
            if (t == null) return Map.of("id", id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("shortName", t.getShortName());
            return m;
        });
    }

    // ── 2. GET /api/players?leagueId= — All players with config (bulk) ──

    @GetMapping("/players")
    public ResponseEntity<List<Map<String, Object>>> getAllPlayers(@RequestParam Integer leagueId) {
        List<PlayerWithTeamData> players = dao.findPlayersWithTeamByLeagueId(leagueId);
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);

        Map<Integer, FantasyPlayerConfig> configMap = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            configMap.put(cfg.getPlayerId(), cfg);
        }

        List<Map<String, Object>> result = new ArrayList<>(players.size());
        for (PlayerWithTeamData p : players) {
            FantasyPlayerConfig cfg = configMap.get(p.id());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.id());
            row.put("name", p.name());
            row.put("role", p.role());
            row.put("teamId", p.teamId());
            row.put("teamName", p.teamName());
            row.put("teamShortName", p.teamShortName());
            row.put("credit", cfg != null ? cfg.getCredit() : null);
            row.put("overseas", cfg != null ? cfg.getOverseas() : false);
            row.put("uncapped", cfg != null ? cfg.getUncapped() : false);
            row.put("totalPoints", cfg != null ? cfg.getTotalPoints() : 0.0);
            row.put("isActive", cfg != null ? cfg.getIsActive() : true);
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    // ── 3. GET /api/me — Current user profile + overall stats ──

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        User user = getAuthenticatedUser();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("firstname", user.getFirstname());
        result.put("lastname", user.getLastname());
        result.put("email", user.getEmail());
        result.put("favteam", user.getFavteam());

        UserOverallStats overall = userOverallStatsRepository.findByUserid(user);
        if (overall != null) {
            result.put("totalPoints", overall.getTotalpoints());
            result.put("boosterLeft", overall.getBoosterleft());
            result.put("transferLeft", overall.getTransferleft());
        }
        return ResponseEntity.ok(result);
    }

    // ── 4. GET /api/me/draft — Current draft team for next match ──

    @GetMapping("/me/draft")
    public ResponseEntity<Map<String, Object>> getMyDraft() {
        User user = getAuthenticatedUser();
        Match nextMatch = matchService.findNextUpcomingMatch();

        Map<String, Object> result = new LinkedHashMap<>();
        if (nextMatch == null) {
            result.put("message", "No upcoming match found");
            return ResponseEntity.ok(result);
        }

        result.put("matchId", nextMatch.getId());
        result.put("matchDate", nextMatch.getDate());
        result.put("matchTime", nextMatch.getTime());
        result.put("matchDesc", nextMatch.getMatchDesc());
        if (nextMatch.getTeamA() != null) {
            result.put("teamA", nextMatch.getTeamA().getShortName());
        }
        if (nextMatch.getTeamB() != null) {
            result.put("teamB", nextMatch.getTeamB().getShortName());
        }

        UserMatchStatsDraft draft = userMatchStatsDraftRepository.findByMatchidAndUserid(nextMatch, user);
        if (draft == null) {
            result.put("hasDraft", false);
            return ResponseEntity.ok(result);
        }

        result.put("hasDraft", true);
        result.put("booster", draft.getBoosterused());
        result.put("transfersUsed", draft.getTransferused());
        result.put("captainId", draft.getCaptainid() != null ? draft.getCaptainid().getId() : null);
        result.put("viceCaptainId", draft.getVicecaptainid() != null ? draft.getVicecaptainid().getId() : null);
        result.put("tripleScorerId", draft.getTripleboosterplayerid() != null ? draft.getTripleboosterplayerid().getId() : null);

        List<Map<String, Object>> playing11 = new ArrayList<>();
        if (draft.getPlaying11() != null) {
            for (Player p : draft.getPlaying11()) {
                Map<String, Object> pRow = new LinkedHashMap<>();
                pRow.put("id", p.getId());
                pRow.put("name", p.getName());
                pRow.put("role", p.getRole());
                playing11.add(pRow);
            }
        }
        result.put("playing11", playing11);

        UserOverallStats overall = userOverallStatsRepository.findByUserid(user);
        result.put("transferLeft", overall != null ? overall.getTransferleft() : 0);
        result.put("boosterLeft", overall != null ? overall.getBoosterleft() : 0);
        result.put("isFreeTransferWindow", freeTransferMatchIds.contains(nextMatch.getId()));
        result.put("usedBoosters", overall != null
                ? overall.getUsedBoosterSet().stream().map(Enum::name).toList()
                : List.of());

        Match prevMatch = matchService.findPreviousMatch(nextMatch);
        if (prevMatch != null) {
            UserMatchStats prevStats = userMatchStatsRepository.findByMatchidAndUserid(prevMatch, user);
            if (prevStats != null && prevStats.getPlaying11() != null) {
                result.put("previousPlaying11",
                        prevStats.getPlaying11().stream().map(Player::getId).toList());
            }
        }

        return ResponseEntity.ok(result);
    }

    // ── 5. GET /api/me/history — All past match results for this user ──

    @GetMapping("/me/history")
    public ResponseEntity<List<Map<String, Object>>> getMyHistory() {
        User user = getAuthenticatedUser();
        List<UserMatchStats> allStats = userMatchStatsRepository.findByUserid(user);

        List<Map<String, Object>> result = new ArrayList<>(allStats.size());
        for (UserMatchStats ums : allStats) {
            Map<String, Object> row = new LinkedHashMap<>();
            Match match = ums.getMatchid();
            row.put("matchId", match != null ? match.getId() : null);
            row.put("date", match != null ? match.getDate() : null);
            if (match != null && match.getTeamA() != null) {
                row.put("teamA", match.getTeamA().getShortName());
            }
            if (match != null && match.getTeamB() != null) {
                row.put("teamB", match.getTeamB().getShortName());
            }
            row.put("matchPoints", ums.getMatchpoints());
            row.put("boosterUsed", ums.getBoosterused());
            row.put("transfersUsed", ums.getTransferused());
            row.put("captainId", ums.getCaptainid() != null ? ums.getCaptainid().getId() : null);
            row.put("viceCaptainId", ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null);

            List<Integer> playerIds = new ArrayList<>();
            if (ums.getPlaying11() != null) {
                for (Player p : ums.getPlaying11()) {
                    playerIds.add(p.getId());
                }
            }
            row.put("playing11", playerIds);
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    // ── 6. GET /api/leaderboard — Overall user rankings ──

    @GetMapping("/leaderboard")
    public ResponseEntity<List<Map<String, Object>>> getLeaderboard() {
        List<UserOverallStats> allStats = userOverallStatsRepository.findAll();
        allStats.sort((a, b) -> {
            double pa = a.getTotalpoints() != null ? a.getTotalpoints() : 0;
            double pb = b.getTotalpoints() != null ? b.getTotalpoints() : 0;
            return Double.compare(pb, pa);
        });

        List<Map<String, Object>> result = new ArrayList<>(allStats.size());
        int rank = 1;
        for (UserOverallStats uos : allStats) {
            User u = uos.getUserid();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("userId", u != null ? u.getId() : null);
            row.put("username", u != null ? u.getUsername() : null);
            row.put("firstname", u != null ? u.getFirstname() : null);
            row.put("totalPoints", uos.getTotalpoints());
            result.add(row);
        }
        return ResponseEntity.ok(result);
    }

    // ── 7. GET /api/points/{matchId} — Player points for a match ──

    @GetMapping("/points/{matchId}")
    public ResponseEntity<List<Map<String, Object>>> getMatchPlayerPoints(@PathVariable Integer matchId) {
        List<PlayerPoints> ppList = playerPointsRepository.findByMatchId(matchId);

        List<Integer> playerIds = new ArrayList<>(ppList.size());
        for (PlayerPoints pp : ppList) {
            playerIds.add(pp.getPlayerId());
        }

        Map<Integer, String> nameMap = new HashMap<>();
        Map<Integer, String> roleMap = new HashMap<>();
        if (!playerIds.isEmpty()) {
            var playerDataList = dao.findPlayersByIds(playerIds);
            for (var pd : playerDataList) {
                nameMap.put(pd.id(), pd.name());
                roleMap.put(pd.id(), pd.role() != null ? pd.role().name() : null);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(ppList.size());
        for (PlayerPoints pp : ppList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", pp.getPlayerId());
            row.put("playerName", nameMap.getOrDefault(pp.getPlayerId(), "Unknown"));
            row.put("role", roleMap.get(pp.getPlayerId()));
            row.put("points", pp.getPlayerpoints());
            result.add(row);
        }

        result.sort((a, b) -> {
            double pa = a.get("points") != null ? (Double) a.get("points") : 0;
            double pb = b.get("points") != null ? (Double) b.get("points") : 0;
            return Double.compare(pb, pa);
        });

        return ResponseEntity.ok(result);
    }

    // ── 8. POST /api/team — View any user's team for a match ──

    @PostMapping("/team")
    public ResponseEntity<Map<String, Object>> getUserTeam(@RequestBody Map<String, Integer> body) {
        Integer userId = body.get("userId");
        Integer matchId = body.get("matchId");
        if (userId == null || matchId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and matchId are required"));
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        Match match = dao.findMatchById(matchId).map(mapper::toMatch).orElse(null);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found"));
        }

        UserMatchStats ums = userMatchStatsRepository.findByMatchidAndUserid(match, user);
        if (ums == null) {
            return ResponseEntity.ok(Map.of("found", false,
                    "message", "No locked team for this user/match"));
        }

        Map<Integer, Double> ppMap = new HashMap<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }

        List<Map<String, Object>> playing11 = new ArrayList<>();
        if (ums.getPlaying11() != null) {
            for (Player p : ums.getPlaying11()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("playerId", p.getId());
                row.put("name", p.getName());
                row.put("role", p.getRole() != null ? p.getRole().name() : null);
                row.put("points", ppMap.getOrDefault(p.getId(), 0.0));

                boolean isCap = ums.getCaptainid() != null && p.getId().equals(ums.getCaptainid().getId());
                boolean isVc = ums.getVicecaptainid() != null && p.getId().equals(ums.getVicecaptainid().getId());
                boolean isTriple = ums.getTripleboosterplayerid() != null
                        && p.getId().equals(ums.getTripleboosterplayerid().getId());
                if (isCap) row.put("tag", "CAPTAIN");
                else if (isVc) row.put("tag", "VICE_CAPTAIN");
                else if (isTriple) row.put("tag", "TRIPLE_SCORER");

                playing11.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("userId", userId);
        result.put("username", user.getUsername());
        result.put("firstname", user.getFirstname());
        result.put("matchId", matchId);
        result.put("matchPoints", ums.getMatchpoints());
        result.put("boosterUsed", ums.getBoosterused());
        result.put("transfersUsed", ums.getTransferused());
        result.put("captainId", ums.getCaptainid() != null ? ums.getCaptainid().getId() : null);
        result.put("viceCaptainId", ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null);
        result.put("playing11", playing11);
        return ResponseEntity.ok(result);
    }

    // ── Helper: extract authenticated user from JWT ──

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new com.cricket.fantasyleague.exception.ResourceNotFoundException("User not found: " + username);
        }
        return user;
    }
}
