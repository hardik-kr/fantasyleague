package com.cricket.fantasyleague.service.masterdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.LeagueData;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MasterDataConfigService {
    private static final Logger logger = LoggerFactory.getLogger(MasterDataConfigService.class);
    private static final double MIN_CREDIT = 7.0;
    private static final double MAX_CREDIT = 11.5;
    private static final Set<String> TIER_A_TEAMS = Set.of(
            "IND", "AUS", "ENG", "NZ", "PAK", "SA", "RSA", "SAF", "WI", "SL",
            "INDW", "AUSW", "ENGW", "NZW", "PAKW", "RSAW", "SAW", "WIW", "SLW");
    private static final Set<String> TIER_B_TEAMS = Set.of(
            "BAN", "AFG", "IRE", "ZIM", "NED", "SCO",
            "BANW", "AFGW", "IREW", "ZIMW", "NEDW", "SCOW");
    private static final Set<String> ELITE_TEAM_CREDIT_CAPS = Set.of(
            "IND", "AUS", "ENG", "NZ", "SA", "RSA", "SAF",
            "INDW", "AUSW", "ENGW", "NZW", "RSAW", "SAW");
    private static final Set<String> STRONG_TEAM_CREDIT_CAPS = Set.of(
            "PAK", "WI", "SL",
            "PAKW", "WIW", "SLW");
    private static final Set<String> DEVELOPING_TEAM_CREDIT_CAPS = Set.of(
            "BAN", "AFG", "IRE", "ZIM", "NED", "SCO",
            "BANW", "AFGW", "IREW", "ZIMW", "NEDW", "SCOW");

    private final CricketMasterDataDao cricketMasterDataDao;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final MasterDataReadService masterDataReadService;
    private final ObjectMapper objectMapper;

    @Value("${fantasy.ipl.gamedayplayers.url:https://fantasy.iplt20.com/classic/api/feed/gamedayplayers?lang=en&tourgamedayId=1}")
    private String gamedayPlayersUrl;

    @Value("${fantasy.cricbuzz.player-profile.enabled:true}")
    private boolean cricbuzzPlayerProfileEnabled;

    @Value("${fantasy.cricbuzz.player-profile.batch-size:20}")
    private int cricbuzzPlayerProfileBatchSize;

    @Value("${fantasy.cricbuzz.player-profile.parallelism:5}")
    private int cricbuzzPlayerProfileParallelism;

    public MasterDataConfigService(
            CricketMasterDataDao cricketMasterDataDao,
            FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
            MasterDataReadService masterDataReadService) {
        this.cricketMasterDataDao = cricketMasterDataDao;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.masterDataReadService = masterDataReadService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public FantasyPlayerConfigInitSummary initializeFantasyPlayerConfigs() {
        List<JsonNode> apiPlayers = fetchDataFromIPL();
        if (apiPlayers.isEmpty()) {
            logger.warn("No players received from external IPL Fantasy API");
            return FantasyPlayerConfigInitSummary.empty();
        }
        logger.info("Fetched {} players from IPL Fantasy API", apiPlayers.size());

        List<LeagueData> leagues = cricketMasterDataDao.findAllLeagues();
        FantasyPlayerConfigInitSummary total = FantasyPlayerConfigInitSummary.empty();

        for (LeagueData league : leagues) {

            if(!league.shortName().contains("IPL"))
                continue ;

            total = total.plus(initializeIplFantasyPlayerConfigs(league, apiPlayers));
        }
        reloadMasterDataCache();
        return total;
    }

    @Transactional
    public FantasyPlayerConfigInitSummary initializeFantasyPlayerConfigs(Integer leagueId) {
        if (leagueId == null) {
            return initializeFantasyPlayerConfigs();
        }

        LeagueData league = cricketMasterDataDao.findLeagueById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        FantasyPlayerConfigInitSummary summary;
        if (isIplLeague(league)) {
            List<JsonNode> apiPlayers = fetchDataFromIPL();
            if (apiPlayers.isEmpty()) {
                logger.warn("No players received from external IPL Fantasy API");
                return FantasyPlayerConfigInitSummary.empty();
            }
            logger.info("Fetched {} players from IPL Fantasy API", apiPlayers.size());
            summary = initializeIplFantasyPlayerConfigs(league, apiPlayers);
        } else {
            summary = initializeNonIplFantasyPlayerConfigs(league);
        }
        reloadMasterDataCache();
        return summary;
    }

    private FantasyPlayerConfigInitSummary initializeIplFantasyPlayerConfigs(
            LeagueData league, List<JsonNode> apiPlayers) {
        List<PlayerData> dbPlayers = cricketMasterDataDao.findPlayersByLeagueId(league.id());
        if (dbPlayers.isEmpty()) {
            logger.info("leagueId={} — no DB players found, skipping", league.id());
            return new FantasyPlayerConfigInitSummary(league.id(), 0, 0, 0);
        }

        Map<String, PlayerData> normalizedIndex = buildNameIndex(dbPlayers);

        Set<Integer> existingPlayerIds = existingPlayerIds(league.id());
        logger.info("leagueId={} — {} existing configs loaded", league.id(), existingPlayerIds.size());

        int created = 0;
        int skipped = 0;

        List<FantasyPlayerConfig> batch = new ArrayList<>();

        for (JsonNode apiPlayer : apiPlayers) {
            String apiName = apiPlayer.path("Name").asText("").trim();
            if (apiName.isBlank()) {
                continue;
            }

            PlayerData matched = findBestMatch(apiName, normalizedIndex, dbPlayers);
            if (matched == null) {
                logger.debug("leagueId={} — no DB match for API player: '{}'", league.id(), apiName);
                skipped++;
                continue;
            }

            if (existingPlayerIds.contains(matched.id())) {
                skipped++;
                continue;
            }

            double credit = apiPlayer.path("Value").asDouble(8.5);
            boolean uncapped = apiPlayer.path("isUnCap").asInt(0) == 1;
            boolean overseas = "1".equals(apiPlayer.path("IS_FP").asText("0"));
            PlayerType type = skillNameToPlayerType(apiPlayer.path("SkillName").asText(""));

            FantasyPlayerConfig config = new FantasyPlayerConfig(
                    matched.id(), league.id(), credit, type, overseas, uncapped);
            batch.add(config);
            existingPlayerIds.add(matched.id());
            created++;
        }

        if (!batch.isEmpty()) {
            fantasyPlayerConfigRepository.saveAll(batch);
        }
        logger.info("leagueId={} — created {} configs from IPL API, skipped {}",
                league.id(), created, skipped);

        return new FantasyPlayerConfigInitSummary(league.id(), created, skipped, dbPlayers.size());
    }

    private FantasyPlayerConfigInitSummary initializeNonIplFantasyPlayerConfigs(LeagueData league) {
        List<PlayerWithTeamData> dbPlayers = cricketMasterDataDao.findPlayersWithTeamByLeagueId(league.id());
        if (dbPlayers.isEmpty()) {
            logger.info("leagueId={} — no DB players found, skipping", league.id());
            return new FantasyPlayerConfigInitSummary(league.id(), 0, 0, 0);
        }

        Set<Integer> existingPlayerIds = existingPlayerIds(league.id());
        List<PlayerConfigWorkItem> missingPlayers = new ArrayList<>();
        int skipped = 0;
        int processed = 0;
        int totalPlayers = dbPlayers.size();

        for (PlayerWithTeamData p : dbPlayers) {
            processed++;
            if (existingPlayerIds.contains(p.id())) {
                skipped++;
                logger.info("leagueId={} — fantasy config progress {}/{} playerId={} name='{}' skipped=existing",
                        league.id(), processed, totalPlayers, p.id(), p.name());
                continue;
            }

            missingPlayers.add(new PlayerConfigWorkItem(processed, totalPlayers, p));
            existingPlayerIds.add(p.id());
        }

        List<PlayerConfigBuildResult> scoredPlayers = scoreMissingNonIplConfigsInBatches(league, missingPlayers);
        List<FantasyPlayerConfig> configs = buildTeamRelativeFantasyConfigs(league, scoredPlayers);
        if (!configs.isEmpty()) {
            fantasyPlayerConfigRepository.saveAll(configs);
        }
        logCreditDistribution(league.id(), configs);

        logger.info("leagueId={} — created {} non-IPL configs, skipped {} existing configs",
                league.id(), configs.size(), skipped);

        return new FantasyPlayerConfigInitSummary(league.id(), configs.size(), skipped, dbPlayers.size());
    }

    private List<PlayerConfigBuildResult> scoreMissingNonIplConfigsInBatches(
            LeagueData league,
            List<PlayerConfigWorkItem> missingPlayers) {
        if (missingPlayers.isEmpty()) {
            return List.of();
        }

        int batchSize = Math.max(1, cricbuzzPlayerProfileBatchSize);
        int parallelism = Math.max(1, cricbuzzPlayerProfileParallelism);
        List<PlayerConfigBuildResult> scoredPlayers = new ArrayList<>();

        logger.info("leagueId={} — processing {} missing fantasy configs in batches of {} with parallelism={}",
                league.id(), missingPlayers.size(), batchSize, parallelism);

        for (int start = 0; start < missingPlayers.size(); start += batchSize) {
            int end = Math.min(start + batchSize, missingPlayers.size());
            int batchNumber = (start / batchSize) + 1;
            int totalBatches = (int) Math.ceil((double) missingPlayers.size() / batchSize);
            List<PlayerConfigWorkItem> currentBatch = missingPlayers.subList(start, end);

            logger.info("leagueId={} — starting fantasy config batch {}/{} players {}-{} of {}",
                    league.id(), batchNumber, totalBatches, start + 1, end, missingPlayers.size());

            List<PlayerConfigBuildResult> batchScores = scoreNonIplConfigBatch(league, currentBatch, parallelism);
            scoredPlayers.addAll(batchScores);

            logger.info("leagueId={} — completed fantasy config scoring batch {}/{} scoredThisBatch={} totalScored={}",
                    league.id(), batchNumber, totalBatches, batchScores.size(), scoredPlayers.size());
        }

        return scoredPlayers;
    }

    private List<PlayerConfigBuildResult> scoreNonIplConfigBatch(
            LeagueData league,
            List<PlayerConfigWorkItem> workItems,
            int parallelism) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, workItems.size()));
        try {
            List<Callable<PlayerConfigBuildResult>> tasks = workItems.stream()
                    .map(item -> (Callable<PlayerConfigBuildResult>) () -> buildNonIplConfig(league, item))
                    .toList();

            List<Future<PlayerConfigBuildResult>> futures = executor.invokeAll(tasks);
            List<PlayerConfigBuildResult> results = new ArrayList<>();

            for (Future<PlayerConfigBuildResult> future : futures) {
                PlayerConfigBuildResult result = future.get();
                results.add(result);
                PlayerWithTeamData p = result.workItem().player();
                logger.info("leagueId={} — fantasy config scoring progress {}/{} playerId={} name='{}' rawScore={} source={}",
                        league.id(), result.workItem().processed(), result.workItem().totalPlayers(), p.id(), p.name(),
                        roundToHalf(result.rawScore()), result.source());
            }

            return results;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initializing non-IPL fantasy configs", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize non-IPL fantasy config batch", ex);
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Fantasy config batch executor did not terminate within timeout");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private PlayerConfigBuildResult buildNonIplConfig(LeagueData league, PlayerConfigWorkItem workItem) {
        PlayerWithTeamData p = workItem.player();
        PlayerType role = p.role() != null ? p.role() : PlayerType.BATTER;
        Optional<CricbuzzPlayerStats> cricbuzzStats = fetchCricbuzzPlayerStats(p.id(), p.name());
        double rawScore = calculateNonIplRawCredit(p, cricbuzzStats);
        return new PlayerConfigBuildResult(
                workItem,
                role,
                rawScore,
                cricbuzzStats.isPresent() ? "cricbuzz" : "heuristic-fallback");
    }

    private List<FantasyPlayerConfig> buildTeamRelativeFantasyConfigs(
            LeagueData league,
            List<PlayerConfigBuildResult> scoredPlayers) {
        Map<Integer, List<PlayerConfigBuildResult>> byTeam = scoredPlayers.stream()
                .collect(Collectors.groupingBy(result -> result.workItem().player().teamId()));

        List<FantasyPlayerConfig> configs = new ArrayList<>();
        byTeam.forEach((teamId, teamPlayers) -> {
            teamPlayers.sort((left, right) -> {
                int scoreCompare = Double.compare(right.rawScore(), left.rawScore());
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return Integer.compare(left.workItem().player().id(), right.workItem().player().id());
            });

            for (int index = 0; index < teamPlayers.size(); index++) {
                PlayerConfigBuildResult result = teamPlayers.get(index);
                PlayerWithTeamData p = result.workItem().player();
                int rank = index + 1;
                double credit = cappedTeamRankCredit(rank, teamPlayers.size(), p.teamShortName(), result.rawScore());
                FantasyPlayerConfig config = new FantasyPlayerConfig(
                        p.id(), league.id(), credit, result.role(), false, false);
                configs.add(config);
                logger.info("leagueId={} — teamRelativeCredit teamId={} team='{}' rank={}/{} playerId={} name='{}' rawScore={} credit={} cap={} source={}",
                        league.id(), p.teamId(), p.teamShortName(), rank, teamPlayers.size(), p.id(), p.name(),
                        roundToHalf(result.rawScore()), credit,
                        teamStrengthCreditCap(p.teamShortName(), rank, result.rawScore()),
                        result.source());
            }
        });
        return configs;
    }

    private double cappedTeamRankCredit(int rank, int teamSize, String teamShortName, double rawScore) {
        double credit = teamRankCredit(rank, teamSize);
        double cap = teamStrengthCreditCap(teamShortName, rank, rawScore);
        return Math.min(credit, cap);
    }

    private double teamStrengthCreditCap(String teamShortName, int rank, double rawScore) {
        String key = normalizeTeamKey(teamShortName);
        if (ELITE_TEAM_CREDIT_CAPS.contains(key)) {
            return 11.5;
        }
        if (STRONG_TEAM_CREDIT_CAPS.contains(key)) {
            return rank == 1 && rawScore >= 11.0 ? 11.0 : 10.5;
        }
        if (DEVELOPING_TEAM_CREDIT_CAPS.contains(key)) {
            return rank == 1 && rawScore >= 10.5 ? 10.5 : 10.0;
        }
        return rank == 1 && rawScore >= 10.75 ? 10.5 : 10.0;
    }

    private String normalizeTeamKey(String teamShortName) {
        return teamShortName == null ? "" : teamShortName.trim().toUpperCase(Locale.ROOT);
    }

    private double teamRankCredit(int rank, int teamSize) {
        if (rank <= 1) return 11.5;
        if (rank <= 3) return 11.0;
        if (rank <= 5) return 10.5;
        if (rank <= 7) return 10.0;
        if (rank <= 9) return 9.5;
        if (rank <= 10) return 9.0;
        if (rank <= 11) return 8.5;
        if (rank <= 12) return 8.0;
        if (rank <= 13) return 7.5;
        return 7.0;
    }

    private void logCreditDistribution(Integer leagueId, List<FantasyPlayerConfig> configs) {
        if (configs.isEmpty()) {
            logger.info("leagueId={} — fantasy config credit distribution: no new configs created", leagueId);
            return;
        }

        Map<Double, Long> countsByCredit = new java.util.TreeMap<>();
        for (FantasyPlayerConfig config : configs) {
            countsByCredit.merge(config.getCredit(), 1L, Long::sum);
        }

        long credit10Plus = configs.stream()
                .filter(config -> config.getCredit() >= 10.0)
                .count();
        long credit11Plus = configs.stream()
                .filter(config -> config.getCredit() >= 11.0)
                .count();

        logger.info("leagueId={} — fantasy config credit distribution countsByCredit={} credit10Plus={} credit11Plus={}",
                leagueId, countsByCredit, credit10Plus, credit11Plus);
    }

    private record PlayerConfigWorkItem(
            int processed,
            int totalPlayers,
            PlayerWithTeamData player
    ) {
    }

    private record PlayerConfigBuildResult(
            PlayerConfigWorkItem workItem,
            PlayerType role,
            double rawScore,
            String source
    ) {
    }

    private Set<Integer> existingPlayerIds(Integer leagueId) {
        Set<Integer> existingPlayerIds = new HashSet<>();
        for (FantasyPlayerConfig fc : fantasyPlayerConfigRepository.findByLeagueId(leagueId)) {
            existingPlayerIds.add(fc.getPlayerId());
        }
        return existingPlayerIds;
    }

    double calculateNonIplCredit(PlayerData player, String teamShortName) {
        PlayerType role = player.role() != null ? player.role() : PlayerType.BATTER;
        return calculateNonIplCredit(player.id(), role, teamShortName);
    }

    double calculateNonIplCredit(PlayerWithTeamData player, Optional<CricbuzzPlayerStats> cricbuzzStats) {
        return clamp(roundToHalf(calculateNonIplRawCredit(player, cricbuzzStats)), MIN_CREDIT, MAX_CREDIT);
    }

    double calculateNonIplRawCredit(PlayerWithTeamData player, Optional<CricbuzzPlayerStats> cricbuzzStats) {
        PlayerType role = player.role() != null ? player.role() : PlayerType.BATTER;
        if (cricbuzzStats.isEmpty()) {
            return calculateNonIplCredit(player.id(), role, player.teamShortName());
        }

        CricbuzzPlayerStats stats = cricbuzzStats.get();
        return roleBase(role)
                + cricbuzzExperienceScore(stats)
                + cricbuzzPerformanceScore(role, stats)
                + iccRankingScore(role, stats)
                + teamTierScore(player.teamShortName());
    }

    double calculateNonIplCredit(Integer playerId, PlayerType role, String teamShortName) {
        double credit = roleBase(role) + experienceScore(playerId) + teamTierScore(teamShortName);
        return clamp(roundToHalf(credit), MIN_CREDIT, MAX_CREDIT);
    }

    private double roleBase(PlayerType role) {
        return role == PlayerType.ALLROUNDER ? 8.5 : 8.0;
    }

    private double experienceScore(Integer playerId) {
        if (playerId == null) return 0.0;
        if (playerId <= 5000) return 2.5;
        if (playerId <= 10000) return 2.0;
        if (playerId <= 15000) return 1.0;
        if (playerId <= 25000) return 0.5;
        if (playerId <= 50000) return 0.0;
        if (playerId <= 999999) return -0.5;
        return -1.0;
    }

    private double teamTierScore(String teamShortName) {
        if (teamShortName == null || teamShortName.isBlank()) return 0.0;
        String key = teamShortName.trim().toUpperCase(Locale.ROOT);
        if (TIER_A_TEAMS.contains(key)) return 0.5;
        if (TIER_B_TEAMS.contains(key)) return 0.25;
        return 0.0;
    }

    private double cricbuzzExperienceScore(CricbuzzPlayerStats stats) {
        int matches = Math.max(stats.battingInt("Matches"), stats.bowlingInt("Matches"));
        if (matches >= 250) return 1.0;
        if (matches >= 150) return 0.75;
        if (matches >= 75) return 0.5;
        if (matches >= 25) return 0.25;
        return 0.0;
    }

    private double cricbuzzPerformanceScore(PlayerType role, CricbuzzPlayerStats stats) {
        double batting = battingPerformanceScore(stats);
        double bowling = bowlingPerformanceScore(stats);

        return switch (role) {
            case ALLROUNDER -> allrounderPerformanceScore(batting, bowling);
            case BOWLER -> bowling;
            case BATTER, KEEPER -> batting;
        };
    }

    private double iccRankingScore(PlayerType role, CricbuzzPlayerStats stats) {
        int rank = switch (role) {
            case BOWLER -> stats.bestCurrentIccRank("bowl");
            case ALLROUNDER -> stats.bestCurrentIccRank("all", "bat", "bowl");
            case BATTER, KEEPER -> stats.bestCurrentIccRank("bat");
        };

        if (rank > 0 && rank <= 3) return 0.5;
        if (rank > 0 && rank <= 10) return 0.25;
        return 0.0;
    }


    private double allrounderPerformanceScore(double batting, double bowling) {
        double primary = Math.max(batting, bowling);
        double secondary = Math.min(batting, bowling);
        return Math.min(2.0, primary * 0.85 + secondary * 0.45);
    }

    private double battingPerformanceScore(CricbuzzPlayerStats stats) {
        int runs = stats.battingInt("Runs");
        double average = stats.battingDouble("Average");
        double strikeRate = stats.battingDouble("SR");

        double score = 0.0;
        if (runs >= 7000) score += 1.35;
        else if (runs >= 5000) score += 1.15;
        else if (runs >= 3000) score += 0.85;
        else if (runs >= 1500) score += 0.55;
        else if (runs >= 500) score += 0.25;

        if (average >= 45) score += 0.4;
        else if (average >= 38) score += 0.3;
        else if (average >= 32) score += 0.15;

        if (strikeRate >= 145) score += 0.25;
        else if (strikeRate >= 130) score += 0.15;
        else if (strikeRate >= 115) score += 0.05;

        return Math.min(2.0, score);
    }

    private double bowlingPerformanceScore(CricbuzzPlayerStats stats) {
        int wickets = stats.bowlingInt("Wickets");
        double average = stats.bowlingDouble("Avg");
        double economy = stats.bowlingDouble("Eco");

        double score = 0.0;
        if (wickets >= 300) score += 1.35;
        else if (wickets >= 200) score += 1.15;
        else if (wickets >= 125) score += 0.85;
        else if (wickets >= 50) score += 0.55;
        else if (wickets >= 15) score += 0.25;

        if (average > 0 && average <= 20) score += 0.35;
        else if (average > 0 && average <= 26) score += 0.25;
        else if (average > 0 && average <= 32) score += 0.1;

        if (economy > 0 && economy <= 5.5) score += 0.2;
        else if (economy > 0 && economy <= 6.5) score += 0.1;

        return Math.min(2.0, score);
    }

    private static double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isIplLeague(LeagueData league) {
        return league.shortName() != null && league.shortName().contains("IPL");
    }

    private void reloadMasterDataCache() {
        try {
            masterDataReadService.reloadMatchesAndCachedPlayerLeagues();
        } catch (Exception ex) {
            logger.warn("Master data cache reload after fantasy config init failed: {}", ex.getMessage());
        }
    }

    // ── Cricbuzz profile stats ──

    Optional<CricbuzzPlayerStats> fetchCricbuzzPlayerStats(PlayerData player) {
        return player == null ? Optional.empty() : fetchCricbuzzPlayerStats(player.id(), player.name());
    }

    Optional<CricbuzzPlayerStats> fetchCricbuzzPlayerStats(Integer playerId, String playerName) {
        if (!cricbuzzPlayerProfileEnabled || playerId == null) {
            return Optional.empty();
        }

        String url = "https://www.cricbuzz.com/profiles/" + playerId + "/" + cricbuzzSlug(playerName);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            ResponseEntity<String> response = new RestTemplate().exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.debug("Cricbuzz profile returned status={} for playerId={}", response.getStatusCode(), playerId);
                return Optional.empty();
            }

            return parseCricbuzzPlayerStats(response.getBody());
        } catch (Exception ex) {
            logger.debug("Failed to fetch Cricbuzz profile for playerId={} name='{}': {}",
                    playerId, playerName, ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<CricbuzzPlayerStats> parseCricbuzzPlayerStats(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode batting = extractEmbeddedJsonObject(source, "\"playerBattingStats\"");
            JsonNode bowling = extractEmbeddedJsonObject(source, "\"playerBowlingStats\"");
            JsonNode playerData = extractEmbeddedJsonObject(source, "\"playerData\"");
            if (batting == null && bowling == null) {
                return Optional.empty();
            }

            return Optional.of(new CricbuzzPlayerStats(
                    toStatMap(batting),
                    toStatMap(bowling),
                    toRankingMap(playerData)));
        } catch (Exception ex) {
            logger.debug("Failed to parse Cricbuzz player stats: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private JsonNode extractEmbeddedJsonObject(String source, String quotedKey) throws Exception {
        int keyIndex = source.indexOf(quotedKey);
        boolean escapedFlightJson = false;
        if (keyIndex < 0) {
            String escapedQuotedKey = quotedKey.replace("\"", "\\\"");
            keyIndex = source.indexOf(escapedQuotedKey);
            escapedFlightJson = keyIndex >= 0;
            quotedKey = escapedQuotedKey;
        }
        if (keyIndex < 0) {
            return null;
        }

        int colonIndex = source.indexOf(':', keyIndex + quotedKey.length());
        if (colonIndex < 0) {
            return null;
        }

        int objectStart = source.indexOf('{', colonIndex);
        if (objectStart < 0) {
            return null;
        }

        int objectEnd = escapedFlightJson
                ? findJsonObjectEndInEscapedJson(source, objectStart)
                : findJsonObjectEnd(source, objectStart);
        if (objectEnd < 0) {
            return null;
        }

        String objectJson = source.substring(objectStart, objectEnd + 1);
        if (escapedFlightJson || objectJson.contains("\\\"")) {
            objectJson = unescapeNextFlightJson(objectJson);
        }
        return objectMapper.readTree(objectJson);
    }

    private int findJsonObjectEnd(String source, int objectStart) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = objectStart; i < source.length(); i++) {
            char c = source.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = inString;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findJsonObjectEndInEscapedJson(String source, int objectStart) {
        int depth = 0;
        for (int i = objectStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String unescapeNextFlightJson(String escapedJson) {
        return escapedJson
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\u0026", "&");
    }

    private Map<String, Map<String, String>> toStatMap(JsonNode statNode) {
        if (statNode == null || statNode.isMissingNode()) {
            return Map.of();
        }

        JsonNode headers = statNode.path("headers");
        JsonNode rows = statNode.path("values");
        if (!headers.isArray() || !rows.isArray() || headers.size() <= 1) {
            return Map.of();
        }

        Map<String, Map<String, String>> stats = new HashMap<>();
        for (int i = 1; i < headers.size(); i++) {
            stats.put(headers.get(i).asText(), new HashMap<>());
        }

        for (JsonNode row : rows) {
            JsonNode values = row.path("values");
            if (!values.isArray() || values.size() == 0) {
                continue;
            }

            String metric = values.get(0).asText();
            for (int i = 1; i < values.size() && i < headers.size(); i++) {
                stats.get(headers.get(i).asText()).put(metric, values.get(i).asText());
            }
        }
        return stats;
    }

    private Map<String, Map<String, Integer>> toRankingMap(JsonNode playerDataNode) {
        if (playerDataNode == null || playerDataNode.isMissingNode()) {
            return Map.of();
        }

        JsonNode rankings = playerDataNode.path("rankings");
        if (!rankings.isObject()) {
            return Map.of();
        }

        Map<String, Map<String, Integer>> result = new HashMap<>();
        rankings.fields().forEachRemaining(entry -> {
            Map<String, Integer> categoryRanks = new HashMap<>();
            entry.getValue().fields().forEachRemaining(rankEntry -> {
                if (isCurrentRankKey(rankEntry.getKey())) {
                    int rank = parsePositiveInt(rankEntry.getValue().asText(""));
                    if (rank > 0) {
                        categoryRanks.put(rankEntry.getKey(), rank);
                    }
                }
            });
            result.put(entry.getKey(), categoryRanks);
        });
        return result;
    }

    private boolean isCurrentRankKey(String key) {
        return key != null && key.endsWith("Rank") && !key.contains("Best") && !key.contains("Diff");
    }

    private int parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String cricbuzzSlug(String name) {
        if (name == null || name.isBlank()) {
            return "player";
        }
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "player" : slug;
    }

    record CricbuzzPlayerStats(
            Map<String, Map<String, String>> batting,
            Map<String, Map<String, String>> bowling,
            Map<String, Map<String, Integer>> rankings
    ) {
        int battingInt(String metric) {
            return maxInt(batting, metric);
        }

        int bowlingInt(String metric) {
            return maxInt(bowling, metric);
        }

        double battingDouble(String metric) {
            return maxDouble(batting, metric);
        }

        double bowlingDouble(String metric) {
            return maxDouble(bowling, metric);
        }

        int bestCurrentIccRank(String... categories) {
            int best = Integer.MAX_VALUE;
            for (String category : categories) {
                Map<String, Integer> categoryRanks = rankings.get(category);
                if (categoryRanks == null) {
                    continue;
                }
                for (Integer rank : categoryRanks.values()) {
                    if (rank != null && rank > 0) {
                        best = Math.min(best, rank);
                    }
                }
            }
            return best == Integer.MAX_VALUE ? 0 : best;
        }

        private static int maxInt(Map<String, Map<String, String>> stats, String metric) {
            return (int) Math.round(maxDouble(stats, metric));
        }

        private static double maxDouble(Map<String, Map<String, String>> stats, String metric) {
            double max = 0.0;
            for (Map<String, String> formatStats : stats.values()) {
                max = Math.max(max, parseNumber(formatStats.get(metric)));
            }
            return max;
        }

        private static double parseNumber(String raw) {
            if (raw == null || raw.isBlank() || raw.equals("-") || raw.equals("-/-")) {
                return 0.0;
            }
            try {
                return Double.parseDouble(raw.replaceAll("[^0-9.]", ""));
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        }
    }

    // ── External API ──

    private List<JsonNode> fetchDataFromIPL() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Accept", "application/json");
            ResponseEntity<String> response = new RestTemplate().exchange(
                    gamedayPlayersUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("IPL Fantasy API returned status={}", response.getStatusCode());
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode playersNode = root.path("Data").path("Value").path("Players");
            if (!playersNode.isArray()) {
                logger.error("Unexpected API response structure — 'Players' array not found");
                return List.of();
            }
            List<JsonNode> result = new ArrayList<>();
            for (JsonNode node : playersNode) {
                result.add(node);
            }
            return result;
        } catch (Exception ex) {
            logger.error("Failed to fetch IPL Fantasy API: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    // ── Name matching ──

    /**
     * Builds a lookup index: normalized full name → PlayerData.
     * Also indexes by last-name token for fallback matching.
     */
    private Map<String, PlayerData> buildNameIndex(List<PlayerData> players) {
        Map<String, PlayerData> index = new HashMap<>();
        for (PlayerData p : players) {
            if (p.name() == null) continue;
            index.put(normalize(p.name()), p);
        }
        return index;
    }

    /**
     * Matching priority:
     * 1. Exact match (case-insensitive, after trimming)
     * 2. DB name contains API name or API name contains DB name
     * 3. Last-name token match (most unique part of a cricket name)
     */
    private PlayerData findBestMatch(String apiName, Map<String, PlayerData> index, List<PlayerData> dbPlayers) {
        String normalizedApi = normalize(apiName);

        PlayerData exact = index.get(normalizedApi);
        if (exact != null) {
            return exact;
        }

        for (PlayerData p : dbPlayers) {
            if (p.name() == null) continue;
            String normalizedDb = normalize(p.name());

            if (normalizedDb.contains(normalizedApi) || normalizedApi.contains(normalizedDb)) {
                return p;
            }
        }

        String apiLastToken = lastToken(normalizedApi);
        if (apiLastToken.length() >= 3) {
            PlayerData lastNameMatch = null;
            int matchCount = 0;
            for (PlayerData p : dbPlayers) {
                if (p.name() == null) continue;
                String dbLast = lastToken(normalize(p.name()));
                if (dbLast.equals(apiLastToken)) {
                    lastNameMatch = p;
                    matchCount++;
                }
            }
            if (matchCount == 1) {
                return lastNameMatch;
            }
        }

        return null;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String lastToken(String normalizedName) {
        int lastSpace = normalizedName.lastIndexOf(' ');
        return lastSpace >= 0 ? normalizedName.substring(lastSpace + 1) : normalizedName;
    }

    // ── Skill/type mapping ──

    private PlayerType skillNameToPlayerType(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return PlayerType.BATTER;
        }
        String upper = skillName.toUpperCase(Locale.ROOT).trim();

        if (upper.contains("WICKET") || upper.contains("KEEPER")) {
            return PlayerType.KEEPER;
        } else if (upper.contains("ALL") && upper.contains("ROUNDER")) {
            return PlayerType.ALLROUNDER;
        } else if (upper.contains("BOWL")) {
            return PlayerType.BOWLER;
        } else if (upper.contains("BAT")) {
            return PlayerType.BATTER;
        }
        return PlayerType.BATTER;
    }

    public record FantasyPlayerConfigInitSummary(
            Integer leagueId,
            int created,
            int skipped,
            int totalPlayers
    ) {
        public static FantasyPlayerConfigInitSummary empty() {
            return new FantasyPlayerConfigInitSummary(null, 0, 0, 0);
        }

        public FantasyPlayerConfigInitSummary plus(FantasyPlayerConfigInitSummary other) {
            if (other == null) return this;
            return new FantasyPlayerConfigInitSummary(
                    null,
                    this.created + other.created,
                    this.skipped + other.skipped,
                    this.totalPlayers + other.totalPlayers);
        }
    }
}
