package com.cricket.fantasyleague.service;

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

import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.TeamData;
import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Service
public class PipelineBenchmarkService {

    private static final Logger logger = LoggerFactory.getLogger(PipelineBenchmarkService.class);
    private static final int ID_BASE = 800_000;
    private static final int PLAYING11_SIZE = 11;

    private final EntityManager em;
    private final CricketMasterDataDao dao;
    private final UserRepository userRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserMatchStatsService userMatchStatsService;
    private final UserOverallPtsService userOverallPtsService;
    private final LiveMatchUserCache liveMatchUserCache;

    public PipelineBenchmarkService(EntityManager em,
                                    CricketMasterDataDao dao,
                                    UserRepository userRepository,
                                    UserMatchStatsRespository userMatchStatsRepository,
                                    UserOverallStatsRepository userOverallStatsRepository,
                                    UserMatchStatsService userMatchStatsService,
                                    UserOverallPtsService userOverallPtsService,
                                    LiveMatchUserCache liveMatchUserCache) {
        this.em = em;
        this.dao = dao;
        this.userRepository = userRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userMatchStatsService = userMatchStatsService;
        this.userOverallPtsService = userOverallPtsService;
        this.liveMatchUserCache = liveMatchUserCache;
    }

    // ── Seed ──

    @Transactional
    public Map<String, Object> seed(int userCount) {
        long start = System.currentTimeMillis();

        Team teamA = new Team(null, "LoadTestTeamA", "LTA");
        em.persist(teamA);
        Team teamB = new Team(null, "LoadTestTeamB", "LTB");
        em.persist(teamB);
        em.flush();

        List<Player> allPlayers = createPlayers(teamA, teamB);
        for (Player p : allPlayers) {
            em.persist(p);
        }
        em.flush();

        Match match = new Match(ID_BASE, LocalDate.now(), LocalTime.of(14, 0),
                "LoadTest Venue", 999, null, null, teamA, teamB);
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
        Match match = em.find(Match.class, matchId);
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

        int userCount = liveMatchUserCache.getUserMatchStats(matchId).size();
        List<Long> calcTimes = new ArrayList<>(iterations);
        List<Long> overallTimes = new ArrayList<>(iterations);
        List<Long> totalTimes = new ArrayList<>(iterations);

        forceGc();
        long heapBeforePipeline = usedHeapBytes();

        for (int i = 0; i < iterations; i++) {
            randomizePlayerPoints(fakePlayerPoints, new Random(i));

            long t0 = System.nanoTime();
            Map<Integer, Double> matchPts = userMatchStatsService.calcMatchUserPointsData(match, fakePlayerPoints);
            long t1 = System.nanoTime();
            userOverallPtsService.calcUserOverallPointsData(match, matchPts);
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

        Match match = em.find(Match.class, ID_BASE);
        if (match != null) em.remove(match);

        List<PlayerData> loadTestPlayersA = dao.findPlayersByTeamName("LoadTestTeamA");
        List<PlayerData> loadTestPlayersB = dao.findPlayersByTeamName("LoadTestTeamB");
        int deletedPlayers = 0;
        for (PlayerData pd : loadTestPlayersA) {
            Player p = em.find(Player.class, pd.id());
            if (p != null) { em.remove(p); deletedPlayers++; }
        }
        for (PlayerData pd : loadTestPlayersB) {
            Player p = em.find(Player.class, pd.id());
            if (p != null) { em.remove(p); deletedPlayers++; }
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
            players.add(em.find(Player.class, pd.id()));
        }
        for (PlayerData pd : dao.findPlayersByTeamName(teamBName)) {
            players.add(em.find(Player.class, pd.id()));
        }
        return players;
    }

    private List<Player> createPlayers(Team teamA, Team teamB) {
        PlayerType[] types = { PlayerType.KEEPER, PlayerType.BATTER, PlayerType.BATTER,
                PlayerType.BATTER, PlayerType.ALLROUNDER, PlayerType.ALLROUNDER,
                PlayerType.BOWLER, PlayerType.BOWLER, PlayerType.BOWLER,
                PlayerType.BATTER, PlayerType.BOWLER, PlayerType.ALLROUNDER };
        List<Player> players = new ArrayList<>(24);
        for (int i = 0; i < 12; i++) {
            players.add(new Player("LT_PlayerA_" + i, teamA, 8.0 + i * 0.5, types[i], i % 3 == 0, false));
            players.add(new Player("LT_PlayerB_" + i, teamB, 8.0 + i * 0.5, types[i], i % 3 == 0, false));
        }
        return players;
    }

    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            User u = new User();
            u.setId(ID_BASE + i);
            u.setName("loadtest_" + i);
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
