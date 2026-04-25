package com.cricket.fantasyleague.controller;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDate;
import static com.cricket.fantasyleague.util.MatchTimeUtils.nowTime;
import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.payload.ApiResponse;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.masterdata.MasterDataConfigService;
import com.cricket.fantasyleague.service.playerpoints.LiveMatchPlayerPointsService;
import com.cricket.fantasyleague.service.testdata.TestDataSeeder;
import com.cricket.fantasyleague.service.usermatchstats.UserMatchStatsService;
import com.cricket.fantasyleague.service.useroverallpts.UserOverallPtsService;
import com.cricket.fantasyleague.service.usertransfer.UserTransferService;
import com.cricket.fantasyleague.service.workflow.LiveMatchWorkflowService;
import com.cricket.fantasyleague.service.workflow.TestWorkflowService;

@RestController
@RequestMapping("/test")
public class TestController {

    private final TestWorkflowService testWorkflowService;
    private final LiveMatchWorkflowService liveMatchWorkflowService;
    private final MatchService matchService;
    private final LiveMatchPlayerPointsService playerPointsService;
    private final UserMatchStatsService userMatchStatsService;
    private final UserOverallPtsService userOverallPtsService;
    private final UserTransferService userTransferService;
    private final MasterDataConfigService masterDataConfigService;
    private final TestDataSeeder testDataSeeder;
    private final LiveMatchCache liveMatchCache;
    private final LiveMatchUserCache liveMatchUserCache;
    private final PlayerPointsRepository playerPointsRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final UserRepository userRepository;

    public TestController(TestWorkflowService testWorkflowService,
                          LiveMatchWorkflowService liveMatchWorkflowService,
                          MatchService matchService,
                          LiveMatchPlayerPointsService playerPointsService,
                          UserMatchStatsService userMatchStatsService,
                          UserOverallPtsService userOverallPtsService,
                          UserTransferService userTransferService,
                          MasterDataConfigService masterDataConfigService,
                          TestDataSeeder testDataSeeder,
                          LiveMatchCache liveMatchCache,
                          LiveMatchUserCache liveMatchUserCache,
                          PlayerPointsRepository playerPointsRepository,
                          UserMatchStatsRespository userMatchStatsRepository,
                          UserOverallStatsRepository userOverallStatsRepository,
                          FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                          UserRepository userRepository) {
        this.testWorkflowService = testWorkflowService;
        this.liveMatchWorkflowService = liveMatchWorkflowService;
        this.matchService = matchService;
        this.playerPointsService = playerPointsService;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.userTransferService = userTransferService;
        this.masterDataConfigService = masterDataConfigService;
        this.testDataSeeder = testDataSeeder;
        this.liveMatchCache = liveMatchCache;
        this.liveMatchUserCache = liveMatchUserCache;
        this.playerPointsRepository = playerPointsRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.userRepository = userRepository;
    }

    // ── TestWorkflowService ──

    @PostMapping("/points/{id}")
    public ResponseEntity<ApiResponse> testScore(@PathVariable Integer id) {
        testWorkflowService.calculateTestPoints(id);
        ApiResponse response = new ApiResponse("success", true, HttpStatus.CREATED.value(), HttpStatus.CREATED);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ── LiveMatchWorkflowService ──

    @PostMapping("/pipeline/{matchId}")
    public ResponseEntity<Map<String, Object>> testPipeline(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        liveMatchWorkflowService.processMatchPipeline(match);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("status", "pipeline complete");
        result.put("elapsedMs", elapsed);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/flush")
    public ResponseEntity<Map<String, Object>> testFlush() {
        long start = System.currentTimeMillis();
        liveMatchWorkflowService.flushCacheToDB();
        return ResponseEntity.ok(Map.of(
                "status", "flush complete",
                "elapsedMs", System.currentTimeMillis() - start));
    }

    @PostMapping("/lockteam/{matchId}")
    public ResponseEntity<Map<String, Object>> testLockTeam(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        liveMatchWorkflowService.lockTeamsForMatch(match);
        return ResponseEntity.ok(Map.of(
                "matchId", matchId,
                "status", "teams locked",
                "elapsedMs", System.currentTimeMillis() - start));
    }

    // ── LiveMatchPlayerPointsService ──

    @PostMapping("/playerpoints/{matchId}")
    public ResponseEntity<Map<String, Object>> testPlayerPoints(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        Map<Integer, Double> pointsMap = playerPointsService.calculatePlayerPoints(match);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("playerCount", pointsMap.size());
        result.put("playerPoints", pointsMap);
        result.put("elapsedMs", elapsed);
        return ResponseEntity.ok(result);
    }

    // ── UserMatchStatsService ──

    @PostMapping("/usermatchpoints/{matchId}")
    public ResponseEntity<Map<String, Object>> testUserMatchPoints(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);
        userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap);
        int userCount = liveMatchUserCache.getAllMatchStatCounts().getOrDefault(matchId, 0);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("userCount", userCount);
        result.put("status", "match points streamed to cache");
        result.put("elapsedMs", elapsed);
        return ResponseEntity.ok(result);
    }

    // ── UserOverallPtsService ──

    @PostMapping("/useroverall/{matchId}")
    public ResponseEntity<Map<String, Object>> testUserOverallPoints(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        Map<Integer, Double> playerPointsMap = playerPointsService.calculatePlayerPoints(match);
        userMatchStatsService.calcMatchUserPointsData(match, playerPointsMap);
        userOverallPtsService.calcUserOverallPointsData(match);
        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("status", "overall points updated");
        result.put("elapsedMs", elapsed);
        return ResponseEntity.ok(result);
    }

    // ── UserTransferService (lock only — transfer requires auth + body) ──

    @PostMapping("/lockmatch/{matchId}")
    public ResponseEntity<Map<String, Object>> testLockMatchTeam(@PathVariable Integer matchId) {
        long start = System.currentTimeMillis();
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        userTransferService.lockMatchTeam(match);
        return ResponseEntity.ok(Map.of(
                "matchId", matchId,
                "status", "match team locked via UserTransferService",
                "elapsedMs", System.currentTimeMillis() - start));
    }

    // ── MatchService ──

    @GetMapping("/match/{matchId}")
    public ResponseEntity<Object> testFindMatch(@PathVariable Integer matchId) {
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", match.getId());
        result.put("date", match.getDate());
        result.put("time", match.getTime());
        result.put("isComplete", match.getIsMatchComplete());
        result.put("teamA", match.getTeamA() != null ? match.getTeamA().getId() : null);
        result.put("teamB", match.getTeamB() != null ? match.getTeamB().getId() : null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/match/today")
    public ResponseEntity<Object> testTodayMatches() {
        List<Match> matches = matchService.findMatchByDate(nowDate());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", nowDate());
        result.put("count", matches.size());
        result.put("matchIds", matches.stream().map(Match::getId).toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/match/upcoming")
    public ResponseEntity<Object> testUpcomingMatch() {
        Match match = matchService.findUpcomingMatch(nowDate(), nowTime());
        if (match == null) {
            return ResponseEntity.ok(Map.of("message", "No upcoming match found"));
        }
        return ResponseEntity.ok(Map.of(
                "matchId", match.getId(),
                "date", match.getDate(),
                "time", match.getTime()));
    }

    @GetMapping("/match/next")
    public ResponseEntity<Object> testNextUpcoming() {
        Match match = matchService.findNextUpcomingMatch();
        if (match == null) {
            return ResponseEntity.ok(Map.of("message", "No next upcoming match"));
        }
        return ResponseEntity.ok(Map.of(
                "matchId", match.getId(),
                "date", match.getDate(),
                "time", match.getTime()));
    }

    // ── MasterDataConfigService ──

    @PostMapping("/masterdata/init")
    public ResponseEntity<Map<String, Object>> testInitMasterData() {
        long start = System.currentTimeMillis();
        masterDataConfigService.initializeFantasyPlayerConfigs();
        return ResponseEntity.ok(Map.of(
                "status", "master data initialized",
                "elapsedMs", System.currentTimeMillis() - start));
    }

    // ── Test Data Seeder ──

    @PostMapping("/seed/{matchId}")
    public ResponseEntity<Map<String, Object>> seedTestData(@PathVariable Integer matchId) {
        return ResponseEntity.ok(testDataSeeder.seedAll(matchId));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedUsersOnly() {
        return ResponseEntity.ok(testDataSeeder.seedAll(null));
    }

    @PostMapping("/seed/draft/{matchId}")
    public ResponseEntity<Map<String, Object>> seedDraftOnly(@PathVariable Integer matchId) {
        return ResponseEntity.ok(testDataSeeder.seedDraftOnly(matchId));
    }

    // ── Cache Status ──

    @GetMapping("/cache/status")
    public ResponseEntity<Map<String, Object>> testCacheStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> matchCache = new LinkedHashMap<>();
        List<Match> todayMatches = liveMatchCache.getTodayMatches();
        matchCache.put("todayMatchCount", todayMatches.size());
        matchCache.put("todayMatchIds", todayMatches.stream().map(Match::getId).toList());
        result.put("liveMatchCache", matchCache);

        Map<String, Object> userCache = new LinkedHashMap<>();
        userCache.put("matchStatCounts", liveMatchUserCache.getAllMatchStatCounts());
        userCache.put("overallStatsCount", liveMatchUserCache.getOverallStatsCount());
        userCache.put("hasDirtyData", liveMatchUserCache.hasDirtyData());
        result.put("liveMatchUserCache", userCache);

        result.put("timestamp", nowDateTime());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cache/evict")
    public ResponseEntity<Map<String, Object>> testEvictAllCaches() {
        liveMatchCache.evictAll();
        liveMatchUserCache.evictAll();
        return ResponseEntity.ok(Map.of(
                "status", "all caches evicted",
                "timestamp", nowDateTime()));
    }

    @PostMapping("/cache/evict/{matchId}")
    public ResponseEntity<Map<String, Object>> testEvictMatchCache(@PathVariable Integer matchId) {
        liveMatchCache.evictMatch(matchId);
        liveMatchUserCache.evictMatch(matchId);
        return ResponseEntity.ok(Map.of(
                "matchId", matchId,
                "status", "match cache evicted",
                "timestamp", nowDateTime()));
    }

    // ── READ: Player Points (DB) ──

    @GetMapping("/view/playerpoints/{matchId}")
    public ResponseEntity<Map<String, Object>> viewPlayerPoints(@PathVariable Integer matchId) {
        List<PlayerPoints> records = playerPointsRepository.findByMatchId(matchId);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (PlayerPoints pp : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", pp.getPlayerId());
            row.put("points", pp.getPlayerpoints());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("playerCount", rows.size());
        result.put("players", rows);
        return ResponseEntity.ok(result);
    }

    // ── READ: User Match Points (DB) ──

    @GetMapping("/view/usermatch/{matchId}")
    public ResponseEntity<Map<String, Object>> viewUserMatchPoints(@PathVariable Integer matchId) {
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }
        List<UserMatchStats> statsList = userMatchStatsRepository.findByMatchid(match);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (UserMatchStats ums : statsList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", ums.getUserid() != null ? ums.getUserid().getId() : null);
            row.put("username", ums.getUserid() != null ? ums.getUserid().getUsername() : null);
            row.put("matchPoints", ums.getMatchpoints());
            row.put("boosterUsed", ums.getBoosterused());
            row.put("transfersUsed", ums.getTransferused());
            row.put("captainId", ums.getCaptainid() != null ? ums.getCaptainid().getId() : null);
            row.put("viceCaptainId", ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null);
            row.put("playing11", ums.getPlaying11() != null
                    ? ums.getPlaying11().stream().map(Player::getId).toList()
                    : List.of());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("userCount", rows.size());
        result.put("users", rows);
        return ResponseEntity.ok(result);
    }

    // ── READ: User Overall Points (DB) ──

    @GetMapping("/view/useroverall")
    public ResponseEntity<Map<String, Object>> viewAllUserOverallPoints() {
        List<UserOverallStats> allStats = userOverallStatsRepository.findAll();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (UserOverallStats uos : allStats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uos.getUserid() != null ? uos.getUserid().getId() : null);
            row.put("username", uos.getUserid() != null ? uos.getUserid().getUsername() : null);
            row.put("totalPoints", uos.getTotalpoints());
            row.put("prevPoints", uos.getPrevpoints());
            row.put("boosterLeft", uos.getBoosterleft());
            row.put("transferLeft", uos.getTransferleft());
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userCount", rows.size());
        result.put("users", rows);
        return ResponseEntity.ok(result);
    }

    // ── READ: User Match History (playing11 + player points + meta) ──

    @GetMapping("/view/usermatch/{userId}/{matchId}")
    public ResponseEntity<Map<String, Object>> viewUserMatchHistory(
            @PathVariable Long userId, @PathVariable Integer matchId) {

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found: " + userId));
        }
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Match not found: " + matchId));
        }

        UserMatchStats ums = userMatchStatsRepository.findByMatchidAndUserid(match, user);
        if (ums == null) {
            return ResponseEntity.ok(Map.of("message",
                    "No match data found for userId=" + userId + " matchId=" + matchId));
        }

        Map<Integer, Double> ppMap = new java.util.HashMap<>();
        List<PlayerPoints> allPP = playerPointsRepository.findByMatchId(matchId);
        for (PlayerPoints pp : allPP) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }

        List<Map<String, Object>> playing11Rows = new java.util.ArrayList<>();
        List<Player> squad = ums.getPlaying11();
        if (squad != null) {
            for (Player p : squad) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("playerId", p.getId());
                row.put("name", p.getName());
                row.put("role", p.getRole());
                row.put("points", ppMap.getOrDefault(p.getId(), 0.0));

                boolean isCaptain = ums.getCaptainid() != null && p.getId().equals(ums.getCaptainid().getId());
                boolean isViceCaptain = ums.getVicecaptainid() != null && p.getId().equals(ums.getVicecaptainid().getId());
                boolean isTripleScorer = ums.getTripleboosterplayerid() != null && p.getId().equals(ums.getTripleboosterplayerid().getId());
                if (isCaptain) row.put("tag", "CAPTAIN");
                else if (isViceCaptain) row.put("tag", "VICE_CAPTAIN");
                else if (isTripleScorer) row.put("tag", "TRIPLE_SCORER");

                playing11Rows.add(row);
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("userId", userId);
        meta.put("username", user.getUsername());
        meta.put("matchId", matchId);
        meta.put("matchPoints", ums.getMatchpoints());
        meta.put("boosterUsed", ums.getBoosterused());
        meta.put("transfersUsed", ums.getTransferused());
        meta.put("captainId", ums.getCaptainid() != null ? ums.getCaptainid().getId() : null);
        meta.put("captainName", ums.getCaptainid() != null ? ums.getCaptainid().getName() : null);
        meta.put("viceCaptainId", ums.getVicecaptainid() != null ? ums.getVicecaptainid().getId() : null);
        meta.put("viceCaptainName", ums.getVicecaptainid() != null ? ums.getVicecaptainid().getName() : null);
        meta.put("tripleScorerId", ums.getTripleboosterplayerid() != null ? ums.getTripleboosterplayerid().getId() : null);
        meta.put("tripleScorer", ums.getTripleboosterplayerid() != null ? ums.getTripleboosterplayerid().getName() : null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meta", meta);
        result.put("playing11", playing11Rows);
        return ResponseEntity.ok(result);
    }

    // ── Health / Data Consistency ──

    @GetMapping("/health/data-consistency")
    public ResponseEntity<Map<String, Object>> checkDataConsistency() {
        Map<Long, Double> correctByUser = new LinkedHashMap<>();
        for (Object[] row : userMatchStatsRepository.sumMatchPointsByUser()) {
            Long userId = ((Number) row[0]).longValue();
            Double sum = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
            correctByUser.put(userId, sum);
        }

        List<UserOverallStats> allStats = userOverallStatsRepository.findAll();
        List<Map<String, Object>> mismatches = new java.util.ArrayList<>();

        for (UserOverallStats uos : allStats) {
            if (uos.getUserid() == null) continue;
            Long userId = uos.getUserid().getId();
            double expected = correctByUser.getOrDefault(userId, 0.0);
            double actual = uos.getTotalpoints() != null ? uos.getTotalpoints() : 0.0;
            double prevPts = uos.getPrevpoints() != null ? uos.getPrevpoints() : 0.0;

            if (Math.abs(actual - expected) > 0.01 || Math.abs(prevPts - expected) > 0.01) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", userId);
                row.put("username", uos.getUserid().getUsername());
                row.put("totalpoints", actual);
                row.put("prevpoints", prevPts);
                row.put("expectedFromMatchStats", expected);
                row.put("totalDiff", actual - expected);
                row.put("prevDiff", prevPts - expected);
                mismatches.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", mismatches.isEmpty() ? "OK" : "WARN");
        result.put("totalUsers", allStats.size());
        result.put("mismatchCount", mismatches.size());
        result.put("mismatches", mismatches);
        result.put("timestamp", nowDateTime());
        return ResponseEntity.ok(result);
    }

    // ── READ: Player Overall Points (from fantasy_player_config.total_points) ──

    @GetMapping("/view/playerpoints/overall/{leagueId}")
    public ResponseEntity<Map<String, Object>> viewPlayerOverallPoints(@PathVariable Integer leagueId) {
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (FantasyPlayerConfig cfg : configs) {
            if (cfg.getTotalPoints() == null || cfg.getTotalPoints() == 0.0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", cfg.getPlayerId());
            row.put("totalPoints", cfg.getTotalPoints());
            row.put("credit", cfg.getCredit());
            row.put("type", cfg.getType());
            rows.add(row);
        }
        rows.sort((a, b) -> Double.compare(
                (Double) b.get("totalPoints"), (Double) a.get("totalPoints")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leagueId", leagueId);
        result.put("playerCount", rows.size());
        result.put("players", rows);
        return ResponseEntity.ok(result);
    }
}
