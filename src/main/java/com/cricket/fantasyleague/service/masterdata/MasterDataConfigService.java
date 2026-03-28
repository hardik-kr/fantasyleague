package com.cricket.fantasyleague.service.masterdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


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
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MasterDataConfigService {
    private static final Logger logger = LoggerFactory.getLogger(MasterDataConfigService.class);

    private final CricketMasterDataDao cricketMasterDataDao;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final ObjectMapper objectMapper;

    @Value("${fantasy.ipl.gamedayplayers.url:https://fantasy.iplt20.com/classic/api/feed/gamedayplayers?lang=en&tourgamedayId=1}")
    private String gamedayPlayersUrl;

    public MasterDataConfigService(
            CricketMasterDataDao cricketMasterDataDao,
            FantasyPlayerConfigRepository fantasyPlayerConfigRepository) {
        this.cricketMasterDataDao = cricketMasterDataDao;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void initializeFantasyPlayerConfigs() {
        List<JsonNode> apiPlayers = fetchDataFromIPL();
        if (apiPlayers.isEmpty()) {
            logger.warn("No players received from external IPL Fantasy API");
            return;
        }
        logger.info("Fetched {} players from IPL Fantasy API", apiPlayers.size());

        List<LeagueData> leagues = cricketMasterDataDao.findAllLeagues();

        for (LeagueData league : leagues) {

            if(!league.shortName().contains("IPL"))
                continue ;

            List<PlayerData> dbPlayers = cricketMasterDataDao.findPlayersByLeagueId(league.id());
            if (dbPlayers.isEmpty()) {
                logger.info("leagueId={} — no DB players found, skipping", league.id());
                continue;
            }

            Map<String, PlayerData> normalizedIndex = buildNameIndex(dbPlayers);

            Set<Integer> existingPlayerIds = new HashSet<>();
            for (FantasyPlayerConfig fc : fantasyPlayerConfigRepository.findByLeagueId(league.id())) {
                existingPlayerIds.add(fc.getPlayerId());
            }
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
            logger.info("leagueId={} — created {} configs from IPL API, skipped {} (no DB match)",
                    league.id(), created, skipped);

            int defaultCreated = 0;
            List<FantasyPlayerConfig> defaultBatch = new ArrayList<>();
            for (PlayerData p : dbPlayers) {
                if (existingPlayerIds.contains(p.id())) {
                    continue;
                }
                PlayerType role = p.role() != null ? p.role() : PlayerType.BATTER;
                FantasyPlayerConfig config = new FantasyPlayerConfig(
                        p.id(), league.id(), 8.0, role, false, false);
                defaultBatch.add(config);
                existingPlayerIds.add(p.id());
                defaultCreated++;
            }
            if (!defaultBatch.isEmpty()) {
                fantasyPlayerConfigRepository.saveAll(defaultBatch);
            }
            logger.info("leagueId={} — created {} default configs for remaining DB players",
                    league.id(), defaultCreated);
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
}
