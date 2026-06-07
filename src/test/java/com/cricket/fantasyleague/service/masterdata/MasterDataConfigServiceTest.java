package com.cricket.fantasyleague.service.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.LeagueData;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

@ExtendWith(MockitoExtension.class)
class MasterDataConfigServiceTest {
    private static final String SMRITI_STATS_SOURCE = """
            window.__STATE__={"playerBattingStats":{"headers":["ROWHEADER","Test","ODI","T20","WPL"],"values":[{"values":["Matches","8","120","166","35"]},{"values":["Runs","635","5411","4333","1023"]},{"values":["Average","48.85","47.88","29.88","31"]},{"values":["SR","63.38","90.37","124.55","136.77"]}]},"playerBowlingStats":{"headers":["ROWHEADER","Test","ODI","T20","WPL"],"values":[{"values":["Matches","8","120","166","35"]},{"values":["Wickets","0","1","0","0"]},{"values":["Avg","0.0","47.0","0.0","0.0"]},{"values":["Eco","4","7.83","0","0"]}]},"playerData":{"rankings":{"bat":{"odiRank":"1","t20Rank":"2","odiBestRank":"1","t20BestRank":"1"},"bowl":{},"all":{}}}};
            """;
    private static final String NEXT_FLIGHT_ESCAPED_STATS_SOURCE = """
            <script>self.__next_f.push([1,"25:[\\\"$\\\",\\\"div\\\",null,{\\\"playerBattingStats\\\":{\\\"headers\\\":[\\\"ROWHEADER\\\",\\\"Test\\\",\\\"ODI\\\",\\\"T20\\\",\\\"WPL\\\"],\\\"values\\\":[{\\\"values\\\":[\\\"Matches\\\",\\\"0\\\",\\\"136\\\",\\\"153\\\",\\\"0\\\"]},{\\\"values\\\":[\\\"Runs\\\",\\\"0\\\",\\\"1983\\\",\\\"1531\\\",\\\"0\\\"]},{\\\"values\\\":[\\\"Average\\\",\\\"0\\\",\\\"19.44\\\",\\\"15.46\\\",\\\"0\\\"]},{\\\"values\\\":[\\\"SR\\\",\\\"0\\\",\\\"58.57\\\",\\\"86.75\\\",\\\"0\\\"]}],\\\"seriesSpinner\\\":[{\\\"seriesName\\\":\\\"Career\\\"}]},\\\"playerBowlingStats\\\":{\\\"headers\\\":[\\\"ROWHEADER\\\",\\\"Test\\\",\\\"ODI\\\",\\\"T20\\\",\\\"WPL\\\"],\\\"values\\\":[{\\\"values\\\":[\\\"Matches\\\",\\\"0\\\",\\\"136\\\",\\\"153\\\",\\\"0\\\"]},{\\\"values\\\":[\\\"Wickets\\\",\\\"0\\\",\\\"21\\\",\\\"34\\\",\\\"0\\\"]},{\\\"values\\\":[\\\"Avg\\\",\\\"0.0\\\",\\\"35.86\\\",\\\"19.88\\\",\\\"0.0\\\"]},{\\\"values\\\":[\\\"Eco\\\",\\\"0\\\",\\\"3.54\\\",\\\"5.11\\\",\\\"0\\\"]}]}}]"])</script>
            """;
    private static final String LEGENDARY_BATTER_STATS_SOURCE = """
            window.__STATE__={"playerBattingStats":{"headers":["ROWHEADER","Test","ODI","T20"],"values":[{"values":["Matches","90","210","260"]},{"values":["Runs","5000","9000","7000"]},{"values":["Average","50","48","42"]},{"values":["SR","70","95","150"]}]},"playerBowlingStats":{"headers":["ROWHEADER","Test","ODI","T20"],"values":[{"values":["Matches","90","210","260"]},{"values":["Wickets","0","0","0"]}]}};
            """;

    @BeforeAll
    static void initSnowflakeGenerator() {
        new SnowflakeIdGenerator(1);
    }

    @Mock
    private CricketMasterDataDao cricketMasterDataDao;

    @Mock
    private FantasyPlayerConfigRepository fantasyPlayerConfigRepository;

    @Mock
    private MasterDataReadService masterDataReadService;

    @InjectMocks
    private MasterDataConfigService service;

    @Test
    void calculateNonIplCreditRoundsAndClampsToAllowedRange() {
        assertThat(service.calculateNonIplCredit(new PlayerData(1, "Elite", PlayerType.BATTER), "IND"))
                .isEqualTo(11.0);
        assertThat(service.calculateNonIplCredit(new PlayerData(2, "Elite AR", PlayerType.ALLROUNDER), "IND"))
                .isEqualTo(11.5);
        assertThat(service.calculateNonIplCredit(new PlayerData(1_461_627, "New Player", PlayerType.BOWLER), "UAE"))
                .isEqualTo(7.0);
    }

    @Test
    void calculateNonIplCreditGivesAllrounderPremiumForSameSignals() {
        double batter = service.calculateNonIplCredit(new PlayerData(12_000, "Batter", PlayerType.BATTER), "BAN");
        double allrounder = service.calculateNonIplCredit(new PlayerData(12_000, "Allrounder", PlayerType.ALLROUNDER), "BAN");

        assertThat(allrounder).isEqualTo(batter + 0.5);
    }

    @Test
    void parseCricbuzzStatsAndUseThemForElitePlayerCredit() {
        MasterDataConfigService.CricbuzzPlayerStats stats = service.parseCricbuzzPlayerStats(SMRITI_STATS_SOURCE)
                .orElseThrow();
        PlayerWithTeamData smriti = new PlayerWithTeamData(
                10_012, "Smriti Mandhana", PlayerType.BATTER, 1, "India Women", "INDW");

        assertThat(stats.battingInt("Matches")).isEqualTo(166);
        assertThat(stats.battingInt("Runs")).isEqualTo(5411);
        assertThat(stats.battingDouble("Average")).isEqualTo(48.85);
        assertThat(stats.bestCurrentIccRank("bat")).isEqualTo(1);
        assertThat(service.calculateNonIplCredit(smriti, java.util.Optional.of(stats))).isEqualTo(11.5);
    }

    @Test
    void onlyRareLegendaryProfilesReachMaxCredit() {
        MasterDataConfigService.CricbuzzPlayerStats stats = service.parseCricbuzzPlayerStats(LEGENDARY_BATTER_STATS_SOURCE)
                .orElseThrow();
        PlayerWithTeamData legendary = new PlayerWithTeamData(
                1, "Legendary Batter", PlayerType.BATTER, 1, "India", "IND");

        assertThat(service.calculateNonIplCredit(legendary, java.util.Optional.of(stats))).isEqualTo(11.5);
    }

    @Test
    void parseCricbuzzStatsFromNextFlightEscapedSource() {
        MasterDataConfigService.CricbuzzPlayerStats stats = service.parseCricbuzzPlayerStats(NEXT_FLIGHT_ESCAPED_STATS_SOURCE)
                .orElseThrow();

        assertThat(stats.battingInt("Matches")).isEqualTo(153);
        assertThat(stats.battingInt("Runs")).isEqualTo(1983);
        assertThat(stats.bowlingInt("Wickets")).isEqualTo(34);
        assertThat(stats.bowlingDouble("Eco")).isEqualTo(5.11);
    }

    @Test
    void calculateNonIplCreditFallsBackToIdHeuristicWhenCricbuzzStatsMissing() {
        PlayerWithTeamData player = new PlayerWithTeamData(
                1_461_627, "New Player", PlayerType.BOWLER, 1, "UAE", "UAE");

        assertThat(service.calculateNonIplCredit(player, java.util.Optional.empty())).isEqualTo(7.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeFantasyPlayerConfigsForNonIplCreatesOnlyMissingRows() {
        ReflectionTestUtils.setField(service, "cricbuzzPlayerProfileEnabled", false);
        LeagueData league = new LeagueData(1, "International", "INTL", "T20", "GLOBAL");
        PlayerWithTeamData existing = new PlayerWithTeamData(101, "Existing", PlayerType.BATTER, 2, "India", "IND");
        PlayerWithTeamData missing = new PlayerWithTeamData(12_000, "Missing", PlayerType.ALLROUNDER, 3, "Bangladesh", "BAN");

        FantasyPlayerConfig existingConfig = new FantasyPlayerConfig(
                existing.id(), league.id(), 9.0, PlayerType.BATTER, false, false);

        when(cricketMasterDataDao.findLeagueById(league.id())).thenReturn(Optional.of(league));
        when(cricketMasterDataDao.findPlayersWithTeamByLeagueId(league.id())).thenReturn(List.of(existing, missing));
        when(fantasyPlayerConfigRepository.findByLeagueId(league.id())).thenReturn(List.of(existingConfig));

        MasterDataConfigService.FantasyPlayerConfigInitSummary summary =
                service.initializeFantasyPlayerConfigs(league.id());

        ArgumentCaptor<List<FantasyPlayerConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(fantasyPlayerConfigRepository).saveAll(captor.capture());

        List<FantasyPlayerConfig> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getPlayerId()).isEqualTo(missing.id());
        assertThat(saved.getFirst().getLeagueId()).isEqualTo(league.id());
        assertThat(saved.getFirst().getType()).isEqualTo(PlayerType.ALLROUNDER);
        assertThat(saved.getFirst().getOverseas()).isFalse();
        assertThat(saved.getFirst().getUncapped()).isFalse();
        assertThat(saved.getFirst().getCredit()).isEqualTo(10.0);
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.totalPlayers()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void initializeFantasyPlayerConfigsDistributesCreditsInsideEachTeam() {
        ReflectionTestUtils.setField(service, "cricbuzzPlayerProfileEnabled", false);
        LeagueData league = new LeagueData(12, "International Women", "INT-W", "T20", "GLOBAL");
        List<PlayerWithTeamData> players = java.util.stream.IntStream.rangeClosed(1, 15)
                .mapToObj(id -> new PlayerWithTeamData(id, "Player " + id, PlayerType.BATTER, 1, "India Women", "INDW"))
                .toList();

        when(cricketMasterDataDao.findLeagueById(league.id())).thenReturn(Optional.of(league));
        when(cricketMasterDataDao.findPlayersWithTeamByLeagueId(league.id())).thenReturn(players);
        when(fantasyPlayerConfigRepository.findByLeagueId(league.id())).thenReturn(List.of());

        service.initializeFantasyPlayerConfigs(league.id());

        ArgumentCaptor<List<FantasyPlayerConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(fantasyPlayerConfigRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(FantasyPlayerConfig::getCredit)
                .containsExactlyInAnyOrder(
                        11.5,
                        11.0, 11.0,
                        10.5, 10.5,
                        10.0, 10.0,
                        9.5, 9.5,
                        9.0,
                        8.5,
                        8.0,
                        7.5,
                        7.0, 7.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lowerRankedTeamsDoNotGetElevenPlusCredits() {
        ReflectionTestUtils.setField(service, "cricbuzzPlayerProfileEnabled", false);
        LeagueData league = new LeagueData(12, "International Women", "INT-W", "T20", "GLOBAL");
        List<PlayerWithTeamData> players = java.util.stream.IntStream.rangeClosed(1, 15)
                .mapToObj(id -> new PlayerWithTeamData(id, "Scotland Player " + id, PlayerType.BATTER, 9, "Scotland Women", "SCOW"))
                .toList();

        when(cricketMasterDataDao.findLeagueById(league.id())).thenReturn(Optional.of(league));
        when(cricketMasterDataDao.findPlayersWithTeamByLeagueId(league.id())).thenReturn(players);
        when(fantasyPlayerConfigRepository.findByLeagueId(league.id())).thenReturn(List.of());

        service.initializeFantasyPlayerConfigs(league.id());

        ArgumentCaptor<List<FantasyPlayerConfig>> captor = ArgumentCaptor.forClass(List.class);
        verify(fantasyPlayerConfigRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(FantasyPlayerConfig::getCredit)
                .containsExactlyInAnyOrder(
                        10.5,
                        10.0, 10.0, 10.0, 10.0, 10.0, 10.0,
                        9.5, 9.5,
                        9.0,
                        8.5,
                        8.0,
                        7.5,
                        7.0, 7.0);
        assertThat(captor.getValue())
                .extracting(FantasyPlayerConfig::getCredit)
                .allMatch(credit -> credit < 11.0);
    }

    @Test
    void initializeFantasyPlayerConfigsForNonIplDoesNotSaveWhenAllRowsExist() {
        ReflectionTestUtils.setField(service, "cricbuzzPlayerProfileEnabled", false);
        LeagueData league = new LeagueData(1, "International", "INTL", "T20", "GLOBAL");
        PlayerWithTeamData player = new PlayerWithTeamData(101, "Existing", PlayerType.BATTER, 2, "India", "IND");
        FantasyPlayerConfig existingConfig = new FantasyPlayerConfig(
                player.id(), league.id(), 9.0, PlayerType.BATTER, false, false);

        when(cricketMasterDataDao.findLeagueById(league.id())).thenReturn(Optional.of(league));
        when(cricketMasterDataDao.findPlayersWithTeamByLeagueId(league.id())).thenReturn(List.of(player));
        when(fantasyPlayerConfigRepository.findByLeagueId(league.id())).thenReturn(List.of(existingConfig));

        MasterDataConfigService.FantasyPlayerConfigInitSummary summary =
                service.initializeFantasyPlayerConfigs(league.id());

        verify(fantasyPlayerConfigRepository, never()).saveAll(anyList());
        assertThat(summary.created()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.totalPlayers()).isEqualTo(1);
    }
}
