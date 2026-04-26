package com.cricket.fantasyleague.service.benchmark;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.cache.DailyLiveMatchTeamCache;
import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.TeamData;
import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;
import com.cricket.fantasyleague.entity.table.season.UserMatchStats;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamRepository;
import com.cricket.fantasyleague.repository.season.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.season.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;

import jakarta.persistence.EntityManager;

import com.cricket.fantasyleague.service.daily.DailyMatchPointsService;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.service.season.UserMatchStatsService;
import com.cricket.fantasyleague.service.season.UserOverallPtsService;

@Service
public class PipelineBenchmarkService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineBenchmarkService.class);
    private static final long ID_BASE = 800_000L;
    private static final int PLAYING11_SIZE = 11;

    private final EntityManager em;
    private final CricketMasterDataDao dao;
    private final CricketEntityMapper cricketEntities;
    private final MatchService matchService;
    private final UserRepository userRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserMatchStatsService userMatchStatsService;
    private final UserOverallPtsService userOverallPtsService;
    private final LiveMatchUserCache liveMatchUserCache;
    private final DailyLiveMatchTeamCache dailyLiveMatchTeamCache;
    private final DailyMatchPointsService dailyMatchPointsService;
    private final DailyUserMatchTeamRepository dailyTeamRepository;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;

    public PipelineBenchmarkService(EntityManager em,
                                    CricketMasterDataDao dao,
                                    CricketEntityMapper cricketEntities,
                                    MatchService matchService,
                                    UserRepository userRepository,
                                    UserMatchStatsRespository userMatchStatsRepository,
                                    UserOverallStatsRepository userOverallStatsRepository,
                                    UserMatchStatsService userMatchStatsService,
                                    UserOverallPtsService userOverallPtsService,
                                    LiveMatchUserCache liveMatchUserCache,
                                    DailyLiveMatchTeamCache dailyLiveMatchTeamCache,
                                    DailyMatchPointsService dailyMatchPointsService,
                                    DailyUserMatchTeamRepository dailyTeamRepository,
                                    FantasyPlayerConfigRepository fantasyPlayerConfigRepository) {
        this.em = em;
        this.dao = dao;
        this.cricketEntities = cricketEntities;
        this.matchService = matchService;
        this.userRepository = userRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.liveMatchUserCache = liveMatchUserCache;
        this.dailyLiveMatchTeamCache = dailyLiveMatchTeamCache;
        this.dailyMatchPointsService = dailyMatchPointsService;
        this.dailyTeamRepository = dailyTeamRepository;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
    }

    // ── Seed ──

    @Transactional
    public Map<String, Object> seed(int userCount) {
        long start = System.currentTimeMillis();

        Team teamA = new Team(null, "LoadTestTeamA", "LTA", null);
        em.persist(teamA);
        Team teamB = new Team(null, "LoadTestTeamB", "LTB", null);
        em.persist(teamB);
        em.flush();

        List<Player> allPlayers = createPlayers(teamA, teamB);
        for (Player p : allPlayers) {
            em.persist(p);
        }
        em.flush();

        createFantasyConfigs(allPlayers);

        Match match = new Match();
        match.setId((int) ID_BASE);
        match.setDate(LocalDate.now());
        match.setTime(LocalTime.of(14, 0));
        match.setTimezone("Asia/Kolkata");
        match.setVenue("LoadTest Venue");
        match.setMatchDesc("LoadTest");
        match.setIsMatchComplete(false);
        match.setResult(null);
        match.setToss(null);
        match.setLeagueId(null);
        match.setMomPlayerId(null);
        match.setTeamA(teamA);
        match.setTeamB(teamB);
        em.persist(match);
        em.flush();

        List<User> users = createUsers(userCount);
        userRepository.saveAll(users);

        Random rng = new Random(42);
        Booster[] boosters = Booster.values();
        List<UserMatchStats> statsList = new ArrayList<>(userCount);
        List<UserOverallStats> overallList = new ArrayList<>(userCount);

        for (int i = 0; i < userCount; i++) {
            User user = users.get(i);
            List<Player> playing11 = pickRandom11(allPlayers, rng);
            Player captain = playing11.get(0);
            Player viceCaptain = playing11.get(1);
            Player tripleBooster = playing11.get(2);
            Booster booster = boosters[rng.nextInt(boosters.length)];

            UserMatchStats ums = new UserMatchStats();
            ums.setId(ID_BASE + 100_000 + i);
            ums.setUserid(user);
            ums.setMatchid(match);
            ums.setBoosterused(booster);
            ums.setTransferused(0);
            ums.setMatchpoints(0.0);
            ums.setCaptainid(captain);
            ums.setVicecaptainid(viceCaptain);
            ums.setTripleboosterplayerid(tripleBooster);
            ums.setPlaying11(playing11);
            statsList.add(ums);

            UserOverallStats uos = new UserOverallStats();
            uos.setId(ID_BASE + 200_000 + i);
            uos.setUserid(user);
            uos.setTotalpoints(0.0);
            uos.setPrevpoints(rng.nextDouble() * 500);
            uos.setBoosterleft(3);
            uos.setTransferleft(5);
            overallList.add(uos);
        }

        userMatchStatsRepository.saveAll(statsList);
        userOverallStatsRepository.saveAll(overallList);

        long elapsed = System.currentTimeMillis() - start;
        logger.info("Seeded {} users in {} ms (matchId={})", userCount, elapsed, ID_BASE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", ID_BASE);
        result.put("userCount", userCount);
        result.put("playerCount", allPlayers.size());
        result.put("seedTimeMs", elapsed);
        return result;
    }

    // ── Benchmark ──

    public Map<String, Object> benchmark(int matchId, int iterations) {
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("Match not found: " + matchId);
        }

        liveMatchUserCache.evictMatch(matchId);
        liveMatchUserCache.evictAll();

        forceGc();
        long heapBeforeWarmup = usedHeapBytes();

        long warmStart = System.currentTimeMillis();
        liveMatchUserCache.warmUp(match);
        long warmMs = System.currentTimeMillis() - warmStart;

        forceGc();
        long heapAfterWarmup = usedHeapBytes();
        long cacheMemoryBytes = heapAfterWarmup - heapBeforeWarmup;

        List<Player> players = loadPlayersForMatch(match);
        Map<Integer, Double> fakePlayerPoints = buildFakePlayerPoints(players);

        Integer userCount = liveMatchUserCache.getAllMatchStatCounts().getOrDefault(matchId, 0);
        List<Long> calcTimes = new ArrayList<>(iterations);
        List<Long> overallTimes = new ArrayList<>(iterations);
        List<Long> totalTimes = new ArrayList<>(iterations);

        forceGc();
        long heapBeforePipeline = usedHeapBytes();

        for (int i = 0; i < iterations; i++) {
            randomizePlayerPoints(fakePlayerPoints, new Random(i));

            long t0 = System.nanoTime();
            userMatchStatsService.calcMatchUserPointsData(match, fakePlayerPoints);
            long t1 = System.nanoTime();
            userOverallPtsService.calcUserOverallPointsData(match);
            long t2 = System.nanoTime();

            calcTimes.add((t1 - t0) / 1_000_000);
            overallTimes.add((t2 - t1) / 1_000_000);
            totalTimes.add((t2 - t0) / 1_000_000);
        }

        forceGc();
        long heapAfterPipeline = usedHeapBytes();
        long pipelineMemoryBytes = heapAfterPipeline - heapBeforePipeline;

        long flushStart = System.currentTimeMillis();
        liveMatchUserCache.flushToDB();
        long flushMs = System.currentTimeMillis() - flushStart;

        Runtime rt = Runtime.getRuntime();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("userCount", userCount);
        result.put("iterations", iterations);
        result.put("warmUpMs", warmMs);
        result.put("calcMatchPts_avg_ms", avg(calcTimes));
        result.put("calcMatchPts_min_ms", Collections.min(calcTimes));
        result.put("calcMatchPts_max_ms", Collections.max(calcTimes));
        result.put("calcOverallPts_avg_ms", avg(overallTimes));
        result.put("totalPipeline_avg_ms", avg(totalTimes));
        result.put("totalPipeline_min_ms", Collections.min(totalTimes));
        result.put("totalPipeline_max_ms", Collections.max(totalTimes));
        result.put("flushToDb_ms", flushMs);
        result.put("calcTimes_ms", calcTimes);
        result.put("verdict", verdictForBallByBall(avg(totalTimes), userCount));

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("jvm_max_heap_MB", rt.maxMemory() / (1024 * 1024));
        memory.put("jvm_total_heap_MB", rt.totalMemory() / (1024 * 1024));
        memory.put("jvm_used_heap_MB", usedHeapBytes() / (1024 * 1024));
        memory.put("jvm_free_heap_MB", rt.freeMemory() / (1024 * 1024));
        memory.put("cache_warmup_delta_KB", cacheMemoryBytes / 1024);
        memory.put("pipeline_working_delta_KB", pipelineMemoryBytes / 1024);

        Map<String, Object> estimated = estimateCacheMemory(userCount, players.size());
        memory.put("estimated_breakdown", estimated);

        result.put("memory", memory);
        return result;
    }

    public Map<String, Object> memoryReport() {
        Runtime rt = Runtime.getRuntime();
        forceGc();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm_max_heap_MB", rt.maxMemory() / (1024 * 1024));
        result.put("jvm_total_heap_MB", rt.totalMemory() / (1024 * 1024));
        result.put("jvm_used_heap_MB", usedHeapBytes() / (1024 * 1024));
        result.put("jvm_free_heap_MB", rt.freeMemory() / (1024 * 1024));

        int matchStatsCount = 0;
        for (var entry : liveMatchUserCache.getAllMatchStatCounts().entrySet()) {
            matchStatsCount += entry.getValue();
        }

        Map<String, Object> cacheState = new LinkedHashMap<>();
        cacheState.put("matchStatsCachedEntries", matchStatsCount);
        cacheState.put("matchStatsByMatch", liveMatchUserCache.getAllMatchStatCounts());
        cacheState.put("overallStatsCachedEntries", liveMatchUserCache.getOverallStatsCount());
        cacheState.put("hasDirtyData", liveMatchUserCache.hasDirtyData());
        result.put("liveMatchUserCache", cacheState);

        if (matchStatsCount > 0) {
            Map<String, Object> estimated = estimateCacheMemory(matchStatsCount, 24);
            result.put("estimated_breakdown", estimated);
        }

        return result;
    }

    // ── Cleanup ──

    @Transactional
    public Map<String, Object> cleanup() {
        liveMatchUserCache.evictAll();

        List<UserMatchStats> stats = userMatchStatsRepository.findAll().stream()
                .filter(s -> s.getId() != null && s.getId() >= ID_BASE + 100_000)
                .toList();
        userMatchStatsRepository.deleteAll(stats);

        List<UserOverallStats> overall = userOverallStatsRepository.findAll().stream()
                .filter(o -> o.getId() != null && o.getId() >= ID_BASE + 200_000)
                .toList();
        userOverallStatsRepository.deleteAll(overall);

        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getId() != null && u.getId() >= ID_BASE)
                .toList();
        userRepository.deleteAll(users);

        List<FantasyPlayerConfig> loadTestConfigs = fantasyPlayerConfigRepository.findAll().stream()
                .filter(c -> c.getPlayerId() != null && c.getPlayerId() >= ID_BASE)
                .toList();
        fantasyPlayerConfigRepository.deleteAll(loadTestConfigs);

        Match match = em.find(Match.class, (int) ID_BASE);
        if (match != null) em.remove(match);

        int deletedPlayers = 0;
        for (PlayerData pd : dao.findAllPlayers()) {
            if (pd.name() != null && pd.name().startsWith("LT_Player")) {
                Player p = em.find(Player.class, pd.id());
                if (p != null) { em.remove(p); deletedPlayers++; }
            }
        }

        for (TeamData td : dao.findAllTeams()) {
            if ("LTA".equals(td.shortName()) || "LTB".equals(td.shortName())) {
                Team t = em.find(Team.class, td.id());
                if (t != null) em.remove(t);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedUsers", users.size());
        result.put("deletedMatchStats", stats.size());
        result.put("deletedOverallStats", overall.size());
        result.put("deletedPlayers", deletedPlayers);
        return result;
    }

    // ── Helpers ──

    private List<Player> loadPlayersForMatch(Match match) {
        String teamAName = match.getTeamA().getName();
        String teamBName = match.getTeamB().getName();
        List<Player> players = new ArrayList<>();
        for (PlayerData pd : dao.findPlayersByTeamName(teamAName)) {
            players.add(cricketEntities.toPlayer(pd));
        }
        for (PlayerData pd : dao.findPlayersByTeamName(teamBName)) {
            players.add(cricketEntities.toPlayer(pd));
        }
        return players;
    }

    private List<Player> createPlayers(Team teamA, Team teamB) {
        PlayerType[] roles = { PlayerType.KEEPER, PlayerType.BATTER, PlayerType.BATTER,
                PlayerType.BATTER, PlayerType.ALLROUNDER, PlayerType.ALLROUNDER,
                PlayerType.BOWLER, PlayerType.BOWLER, PlayerType.BOWLER,
                PlayerType.BATTER, PlayerType.BOWLER, PlayerType.ALLROUNDER };
        List<Player> players = new ArrayList<>(24);
        for (int i = 0; i < 12; i++) {
            Player pA = new Player(null, "LT_PlayerA_" + i, roles[i]);
            players.add(pA);
            Player pB = new Player(null, "LT_PlayerB_" + i, roles[i]);
            players.add(pB);
        }
        return players;
    }

    private void createFantasyConfigs(List<Player> allPlayers) {
        PlayerType[] types = { PlayerType.KEEPER, PlayerType.BATTER, PlayerType.BATTER,
                PlayerType.BATTER, PlayerType.ALLROUNDER, PlayerType.ALLROUNDER,
                PlayerType.BOWLER, PlayerType.BOWLER, PlayerType.BOWLER,
                PlayerType.BATTER, PlayerType.BOWLER, PlayerType.ALLROUNDER };
        for (int i = 0; i < allPlayers.size(); i++) {
            Player p = allPlayers.get(i);
            FantasyPlayerConfig cfg = new FantasyPlayerConfig(
                    p.getId(), null, 8.0 + (i / 2) * 0.5,
                    types[i % 12], i % 3 == 0, false);
            fantasyPlayerConfigRepository.save(cfg);
        }
    }

    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            User u = new User();
            u.setId(ID_BASE + i);
            u.setUsername("loadtest_" + i);
            u.setFirstname("Load");
            u.setLastname("Test" + i);
            u.setEmail("lt" + i + "@test.com");
            u.setPassword("pwd");
            u.setPhonenumber("0000000000");
            u.setFavteam("LTA");
            u.setRole(UserRole.USER);
            users.add(u);
        }
        return users;
    }

    private List<Player> pickRandom11(List<Player> pool, Random rng) {
        List<Player> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        return new ArrayList<>(shuffled.subList(0, Math.min(PLAYING11_SIZE, shuffled.size())));
    }

    private Map<Integer, Double> buildFakePlayerPoints(List<Player> players) {
        Map<Integer, Double> map = new HashMap<>(players.size());
        Random rng = new Random(0);
        for (Player p : players) {
            map.put(p.getId(), rng.nextDouble() * 100);
        }
        return map;
    }

    private void randomizePlayerPoints(Map<Integer, Double> map, Random rng) {
        for (Map.Entry<Integer, Double> entry : map.entrySet()) {
            entry.setValue(rng.nextDouble() * 150);
        }
    }

    private long avg(List<Long> values) {
        return (long) values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private String verdictForBallByBall(long avgMs, int userCount) {
        if (avgMs < 500) {
            return String.format("PASS - %d users processed in %d ms avg (well under 20s ball interval)", userCount, avgMs);
        } else if (avgMs < 5000) {
            return String.format("MARGINAL - %d users processed in %d ms avg (fits in 20s but tight)", userCount, avgMs);
        } else {
            return String.format("FAIL - %d users processed in %d ms avg (exceeds ball interval)", userCount, avgMs);
        }
    }

    // ── Memory helpers ──

    private void forceGc() {
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        System.gc();
    }

    private long usedHeapBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    // ────────────────────────────────────────────────────────────────────
    // Daily Challenge benchmark — parity coverage for the
    // DailyLiveMatchTeamCache + DailyMatchPointsService hot path.
    //
    // <p>Methodology mirrors {@link #benchmark(int, int)}: seed → warm-up →
    // N-tick recompute pass → flush, capturing latencies and heap deltas at
    // each phase. The asserts the daily branch must satisfy at 100K
    // (per the scaling plan, section 1a/1b):
    //   1. Tick path issues NO {@code UPDATE daily_user_match_team} queries
    //      — recompute mutates only the cache; markDirty is a flag flip.
    //   2. Heap delta over a 60-tick pass is near-zero (chunk DTOs are
    //      short-lived; GC reclaims them between ticks).
    //   3. flushDirtyToDB emits a single batched UPDATE per chunk
    //      (chunkSize = fantasy.cache.flush.batch-size). At 100K rows /
    //      10K batch size → 10 batched UPDATEs in the periodic flush.
    //   4. At N=0 (no daily drafts saved) the entire daily branch
    //      contributes zero heap, zero CPU, zero DB ops — the most common
    //      "low-engagement match" load profile.
    // ────────────────────────────────────────────────────────────────────

    /**
     * Seeds {@code userCount} {@link DailyUserMatchTeam} rows for the
     * benchmark match (id == {@link #ID_BASE}). Reuses the users + match +
     * players already created by {@link #seed(int)}; call {@code seed(N)}
     * first. Idempotent: re-running drops and re-inserts the daily rows
     * for this match.
     */
    @Transactional
    public Map<String, Object> seedDaily(int userCount) {
        long start = System.currentTimeMillis();

        Match match = em.find(Match.class, (int) ID_BASE);
        if (match == null) {
            throw new IllegalArgumentException("Run /api/loadtest/seed first to create the benchmark match");
        }

        // Drop any prior daily rows for this match so seedDaily is idempotent.
        long previous = dailyTeamRepository.countByMatchId((int) ID_BASE);
        if (previous > 0) {
            em.createQuery("DELETE FROM DailyUserMatchTeam t WHERE t.match.id = :mid")
                    .setParameter("mid", (int) ID_BASE)
                    .executeUpdate();
            em.flush();
        }

        List<Player> allPlayers = loadPlayersForMatch(match);
        if (allPlayers.size() < PLAYING11_SIZE) {
            throw new IllegalArgumentException("Match has fewer than 11 players seeded — re-run /api/loadtest/seed");
        }

        Random rng = new Random(7);
        List<DailyUserMatchTeam> teams = new ArrayList<>(userCount);
        for (int i = 0; i < userCount; i++) {
            User userRef = em.getReference(User.class, ID_BASE + i);
            List<Player> playing11 = pickRandom11(allPlayers, rng);
            DailyUserMatchTeam team = new DailyUserMatchTeam(
                    userRef, match,
                    playing11.get(0).getId(),
                    playing11.get(1).getId(),
                    playing11.stream().map(Player::getId).toList());
            team.setId(ID_BASE + 300_000 + i);
            teams.add(team);
        }
        dailyTeamRepository.saveAll(teams);
        long elapsed = System.currentTimeMillis() - start;
        logger.info("Seeded {} daily teams for matchId={} in {} ms (replaced {})",
                userCount, ID_BASE, elapsed, previous);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", ID_BASE);
        result.put("dailyTeamCount", userCount);
        result.put("replacedExisting", previous);
        result.put("seedTimeMs", elapsed);
        return result;
    }

    /**
     * Run the full daily-mode pipeline benchmark for {@code matchId}:
     * warm cache → run {@code iterations} ticks of {@code updateForLiveMatch}
     * → flush. Returns latencies, heap deltas, and a verdict line.
     *
     * <p>Tolerates the N=0 case: when no daily teams exist for the match,
     * warm-up early-exits and tick latencies are reported as zero — the
     * test profile that proves "matches with no daily participants
     * contribute zero overhead".
     */
    public Map<String, Object> dailyBenchmark(int matchId, int iterations) {
        Match match = matchService.findMatchById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("Match not found: " + matchId);
        }

        dailyLiveMatchTeamCache.evictAll();

        forceGc();
        long heapBeforeWarmup = usedHeapBytes();

        long warmStart = System.currentTimeMillis();
        dailyLiveMatchTeamCache.warmUp(match);
        long warmMs = System.currentTimeMillis() - warmStart;

        forceGc();
        long heapAfterWarmup = usedHeapBytes();
        long cacheMemoryBytes = heapAfterWarmup - heapBeforeWarmup;

        int cachedCount = dailyLiveMatchTeamCache.size(matchId);

        List<Player> players = loadPlayersForMatch(match);
        Map<Integer, Double> fakePlayerPoints = buildFakePlayerPoints(players);

        List<Long> tickTimes = new ArrayList<>(iterations);

        forceGc();
        long heapBeforePipeline = usedHeapBytes();

        for (int i = 0; i < iterations; i++) {
            randomizePlayerPoints(fakePlayerPoints, new Random(i));
            long t0 = System.nanoTime();
            // Skip-if-empty short-circuit: invoking updateForLiveMatch on an
            // unwarmed/empty match must be a fast no-op (the asymmetry
            // contract from plan section 1b).
            dailyMatchPointsService.updateForLiveMatch(matchId, fakePlayerPoints);
            long t1 = System.nanoTime();
            tickTimes.add((t1 - t0) / 1_000_000);
        }

        forceGc();
        long heapAfterPipeline = usedHeapBytes();
        long pipelineMemoryBytes = heapAfterPipeline - heapBeforePipeline;

        long flushStart = System.currentTimeMillis();
        dailyLiveMatchTeamCache.flushDirtyToDB();
        long flushMs = System.currentTimeMillis() - flushStart;

        Runtime rt = Runtime.getRuntime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("cachedTeamCount", cachedCount);
        result.put("iterations", iterations);
        result.put("warmUpMs", warmMs);
        result.put("tick_avg_ms", tickTimes.isEmpty() ? 0L : avg(tickTimes));
        result.put("tick_min_ms", tickTimes.isEmpty() ? 0L : Collections.min(tickTimes));
        result.put("tick_max_ms", tickTimes.isEmpty() ? 0L : Collections.max(tickTimes));
        result.put("flushDirtyToDb_ms", flushMs);
        result.put("tick_times_ms", tickTimes);
        result.put("verdict", verdictForDaily(
                tickTimes.isEmpty() ? 0L : avg(tickTimes), cachedCount));

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("jvm_max_heap_MB", rt.maxMemory() / (1024 * 1024));
        memory.put("jvm_used_heap_MB", usedHeapBytes() / (1024 * 1024));
        memory.put("daily_cache_warmup_delta_KB", cacheMemoryBytes / 1024);
        memory.put("daily_pipeline_working_delta_KB", pipelineMemoryBytes / 1024);
        memory.put("daily_estimated_breakdown", estimateDailyCacheMemory(cachedCount));

        result.put("memory", memory);
        return result;
    }

    /**
     * Final-flush + evict path. Heap should return to pre-warmup baseline.
     * Mirrors what {@code LiveMatchWorkflowService.finalizeMatchCompletion}
     * triggers at match COMPLETE.
     */
    public Map<String, Object> dailyEvict(int matchId) {
        forceGc();
        long heapBefore = usedHeapBytes();
        dailyLiveMatchTeamCache.finalFlushAndEvict(matchId);
        forceGc();
        long heapAfter = usedHeapBytes();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", matchId);
        result.put("heap_before_evict_MB", heapBefore / (1024 * 1024));
        result.put("heap_after_evict_MB", heapAfter / (1024 * 1024));
        result.put("released_KB", (heapBefore - heapAfter) / 1024);
        return result;
    }

    @Transactional
    public Map<String, Object> cleanupDaily() {
        dailyLiveMatchTeamCache.evictAll();

        long deleted = em.createQuery(
                "DELETE FROM DailyUserMatchTeam t WHERE t.id >= :base")
                .setParameter("base", ID_BASE + 300_000)
                .executeUpdate();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedDailyTeams", deleted);
        return result;
    }

    /**
     * Heap budget estimator for the daily cache, mirroring
     * {@link #estimateCacheMemory(int, int)}. Per-team payload (DTO with id,
     * userId, captain/vc, matchPoints, 11×Integer playing11) ≈ 250 bytes.
     */
    private Map<String, Object> estimateDailyCacheMemory(int userCount) {
        long perUserBytes = 250;
        long mapEntryOverhead = 64;
        long total = (long) userCount * (perUserBytes + mapEntryOverhead);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("dailyMatchTeam_perEntry_bytes", perUserBytes + mapEntryOverhead);
        breakdown.put("dailyMatchTeam_total_KB", total / 1024);
        breakdown.put("dailyMatchTeam_total_MB", total / (1024 * 1024));
        return breakdown;
    }

    private String verdictForDaily(long avgTickMs, int cachedCount) {
        if (cachedCount == 0) {
            return "PASS - empty case (N=0): tick path short-circuited, daily branch contributed zero work";
        }
        if (avgTickMs < 200) {
            return String.format("PASS - %d cached teams recomputed in %d ms avg (well within 30s tick interval)",
                    cachedCount, avgTickMs);
        } else if (avgTickMs < 2000) {
            return String.format("MARGINAL - %d cached teams recomputed in %d ms avg",
                    cachedCount, avgTickMs);
        }
        return String.format("FAIL - %d cached teams took %d ms avg per tick — investigate chunk size",
                cachedCount, avgTickMs);
    }

    private Map<String, Object> estimateCacheMemory(int userCount, int playerCount) {
        long perUserMatchStats = 196;
        long perUserOverallStats = 48;
        long perPlayer = 78;
        long mapEntryOverhead = 64;

        long userMatchStatsMem = (long) userCount * (perUserMatchStats + mapEntryOverhead);
        long userOverallStatsMem = (long) userCount * (perUserOverallStats + mapEntryOverhead);
        long playerCacheMem = (long) playerCount * (perPlayer + mapEntryOverhead);

        long totalEstimated = userMatchStatsMem + userOverallStatsMem + playerCacheMem;

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("userMatchStats_KB", userMatchStatsMem / 1024);
        breakdown.put("userMatchStats_perEntry_bytes", perUserMatchStats + mapEntryOverhead);
        breakdown.put("userOverallStats_KB", userOverallStatsMem / 1024);
        breakdown.put("userOverallStats_perEntry_bytes", perUserOverallStats + mapEntryOverhead);
        breakdown.put("playerCache_KB", playerCacheMem / 1024);
        breakdown.put("total_estimated_KB", totalEstimated / 1024);
        breakdown.put("total_estimated_MB", totalEstimated / (1024 * 1024));
        return breakdown;
    }
}
