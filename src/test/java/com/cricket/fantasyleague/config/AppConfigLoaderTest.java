package com.cricket.fantasyleague.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cricket.fantasyleague.entity.enums.Booster;

@ExtendWith(MockitoExtension.class)
class AppConfigLoaderTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppConfig appConfig = new AppConfig();

    @Test
    void loadConfigPopulatesGlobalAppConfigFromConfigDb() {
        when(jdbcTemplate.queryForMap(
                eq("""
                        SELECT id, active_league_id, `year`, status
                        FROM season_config
                        WHERE id = 1
                        """),
                anyMap()))
                .thenReturn(Map.of(
                        "id", 1,
                        "active_league_id", 12,
                        "year", 2026,
                        "status", "ACTIVE"));

        when(jdbcTemplate.queryForMap(
                eq("""
                        SELECT id, league_id, name, total_transfer, booster, free_transfer_match_id
                        FROM all_tournament_config
                        WHERE league_id = :leagueId
                        """),
                eq(Map.of("leagueId", 12))))
                .thenReturn(Map.of(
                        "id", 1,
                        "league_id", 12,
                        "name", "ICC Women's T20 World Cup",
                        "total_transfer", 60,
                        "booster", """
                                [
                                  {"DOUBLE_UP":{"count":2,"active":true}},
                                  {"POWER_BATTER":{"count":1,"active":false}},
                                  {"POWER_BOWLER":{"count":0,"active":true}}
                                ]
                                """,
                        "free_transfer_match_id", "121774, 122016"));

        new AppConfigLoader(jdbcTemplate, objectMapper, appConfig).loadConfig();

        assertThat(appConfig.getActiveLeagueId()).isEqualTo(12);
        assertThat(appConfig.getName()).isEqualTo("ICC Women's T20 World Cup");
        assertThat(appConfig.getYear()).isEqualTo(2026);
        assertThat(appConfig.getStatus()).isEqualTo("ACTIVE");
        assertThat(appConfig.getTotalTransfer()).isEqualTo(60);
        assertThat(appConfig.getTotalBooster()).isEqualTo(2);
        assertThat(appConfig.getActiveBoosters()).containsExactly(new AppConfig.BoosterConfig("DOUBLE_UP", 2));
        assertThat(appConfig.getInitialBoosterLeftDetail()).containsEntry("DOUBLE_UP", 2);
        assertThat(appConfig.isBoosterActive(Booster.DOUBLE_UP)).isTrue();
        assertThat(appConfig.isBoosterActive(Booster.POWER_BATTER)).isFalse();
        assertThat(appConfig.isBoosterActive(Booster.POWER_BOWLER)).isFalse();
        assertThat(appConfig.getBooster().isArray()).isTrue();
        assertThat(appConfig.getFreeTransferMatchIds()).containsExactlyInAnyOrder(121774, 122016);
        assertThat(appConfig.isFreeTransferMatch(121774)).isTrue();
    }

    @Test
    void loadConfigRejectsInvalidBoosterJson() {
        when(jdbcTemplate.queryForMap(
                eq("""
                        SELECT id, active_league_id, `year`, status
                        FROM season_config
                        WHERE id = 1
                        """),
                anyMap()))
                .thenReturn(Map.of(
                        "id", 1,
                        "active_league_id", 12,
                        "year", 2026,
                        "status", "ACTIVE"));

        when(jdbcTemplate.queryForMap(
                eq("""
                        SELECT id, league_id, name, total_transfer, booster, free_transfer_match_id
                        FROM all_tournament_config
                        WHERE league_id = :leagueId
                        """),
                eq(Map.of("leagueId", 12))))
                .thenReturn(Map.of(
                        "id", 1,
                        "league_id", 12,
                        "name", "World Cup",
                        "total_transfer", 60,
                        "booster", "{\"doubleUp\":{\"count\":2,\"active\":true}}",
                        "free_transfer_match_id", ""));

        assertThatThrownBy(() -> new AppConfigLoader(jdbcTemplate, objectMapper, appConfig).loadConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("booster must be a JSON array");
    }
}
