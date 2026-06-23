package com.cricket.fantasyleague.config;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AppConfigLoader {

    private static final String SEASON_CONFIG_SQL = """
            SELECT id, active_league_id, `year`, status
            FROM season_config
            WHERE id = 1
            """;

    private static final String TOURNAMENT_CONFIG_SQL = """
            SELECT id, league_id, name, total_transfer, booster, free_transfer_match_id
            FROM all_tournament_config
            WHERE league_id = :leagueId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    public AppConfigLoader(
            @Qualifier("fantasyConfigJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AppConfig appConfig) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.appConfig = appConfig;
    }

    public void loadConfig() {
        Map<String, Object> seasonRow = querySeasonConfig();
        Integer activeLeagueId = requireInteger(seasonRow, "active_league_id");

        Map<String, Object> tournamentRow = queryTournamentConfig(activeLeagueId);
        Integer totalTransfer = requireInteger(tournamentRow, "total_transfer");
        JsonNode booster = parseBooster(requireString(tournamentRow, "booster"));
        Set<Integer> freeTransferMatchIds = parseFreeTransferMatchIds(tournamentRow.get("free_transfer_match_id"));

        appConfig.load(new AppConfig.Snapshot(
                activeLeagueId,
                requireString(tournamentRow, "name"),
                requireInteger(seasonRow, "year"),
                requireString(seasonRow, "status"),
                totalTransfer,
                booster,
                freeTransferMatchIds));
    }

    private Map<String, Object> querySeasonConfig() {
        try {
            return jdbcTemplate.queryForMap(SEASON_CONFIG_SQL, Map.of());
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException("Missing current tournament row in fantasyleague_config.season_config for id=1", ex);
        }
    }

    private Map<String, Object> queryTournamentConfig(Integer leagueId) {
        try {
            return jdbcTemplate.queryForMap(TOURNAMENT_CONFIG_SQL, Map.of("leagueId", leagueId));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException(
                    "Missing tournament config in fantasyleague_config.all_tournament_config for league_id=" + leagueId,
                    ex);
        }
    }

    private JsonNode parseBooster(String boosterJson) {
        try {
            JsonNode booster = objectMapper.readTree(boosterJson);
            if (!booster.isArray()) {
                throw new IllegalStateException("all_tournament_config.booster must be a JSON array");
            }
            return booster;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid all_tournament_config.booster JSON", ex);
        }
    }

    private Set<Integer> parseFreeTransferMatchIds(Object rawValue) {
        if (rawValue == null || !StringUtils.hasText(rawValue.toString())) {
            return Set.of();
        }

        Set<Integer> matchIds = new LinkedHashSet<>();
        for (String token : rawValue.toString().split(",")) {
            String trimmed = token.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            try {
                matchIds.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(
                        "Invalid free_transfer_match_id value '" + trimmed + "'. Expected comma-separated integers.",
                        ex);
            }
        }
        return matchIds;
    }

    private Integer requireInteger(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            throw new IllegalStateException("Missing required config column: " + column);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer config column: " + column + "=" + value, ex);
        }
    }

    private String requireString(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null || !StringUtils.hasText(value.toString())) {
            throw new IllegalStateException("Missing required config column: " + column);
        }
        return value.toString().trim();
    }
}
