package com.cricket.fantasyleague.service.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.cricket.fantasyleague.cache.store.CacheStoreFactory;
import com.cricket.fantasyleague.cache.store.InMemoryCacheStore;
import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MasterDataCacheServiceTest {

    @Mock
    private CricketMasterDataDao dao;
    @Mock
    private CricketEntityMapper mapper;
    @Mock
    private FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    @Mock
    private CacheStoreFactory cacheStoreFactory;

    private LiveMasterCacheScope liveMasterCacheScope;

    private MasterDataCacheMetrics metrics;

    @BeforeEach
    void metrics() {
        liveMasterCacheScope = new LiveMasterCacheScope();
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        metrics = new MasterDataCacheMetrics(provider);
    }

    private void stubMatchStore() {
        when(cacheStoreFactory.create(eq("master:matches:v1"), eq(Integer.class), eq(MatchResponse.class)))
                .thenReturn(new InMemoryCacheStore<>());
    }

    private void stubLeaguePlayerStore() {
        when(cacheStoreFactory.create(
                argThat(ns -> ns != null && ns.startsWith("master:players:league:")),
                eq(Integer.class),
                eq(PlayerResponse.class)))
                .thenReturn(new InMemoryCacheStore<>());
    }

    @Test
    void disabledAlwaysHitsDaoForMatches() {
        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, false, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.getAllMatchesWithTeams();

        verify(dao, times(2)).findAllMatches();
    }

    @Test
    void enabledSecondReadUsesCache() {
        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.getAllMatchesWithTeams();

        verify(dao, times(1)).findAllMatches();
    }

    @Test
    void refreshWhenLiveUsesScopedRefreshNotFindAllMatches() {
        stubMatchStore();
        stubLeaguePlayerStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(Optional.empty());
        when(dao.findMatchById(1)).thenReturn(Optional.of(sampleMatch(1)));
        when(dao.findTeamById(2)).thenReturn(Optional.empty());
        List<Integer> playerIds = List.of(5);
        when(dao.findPlayersWithTeamByLeagueIdAndPlayerIds(2, playerIds)).thenReturn(List.of(
                new PlayerWithTeamData(5, "P1", PlayerType.BATTER, 1, "Team", "TM")));
        when(fantasyPlayerConfigRepository.findByLeagueIdAndPlayerIdIn(eq(2), eq(playerIds))).thenReturn(List.of());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        Match m = new Match();
        m.setId(1);
        m.setLeagueId(2);
        liveMasterCacheScope.updateFromPlayerPointsMap(m, Map.of(5, 1.0));

        svc.getAllMatchesWithTeams();
        svc.refreshIfLiveMatchActive();

        verify(dao, times(1)).findAllMatches();
        verify(dao).findMatchById(1);
        verify(dao).findPlayersWithTeamByLeagueIdAndPlayerIds(2, playerIds);
    }

    @Test
    void refreshWhenNotLiveDoesNothing() {
        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.refreshIfLiveMatchActive();

        verify(dao, times(1)).findAllMatches();
    }

    @Test
    void enabledPlayerSecondReadUsesCache() {
        stubMatchStore();
        stubLeaguePlayerStore();
        int leagueId = 7;
        when(dao.findPlayersWithTeamByLeagueId(leagueId)).thenReturn(List.of(
                new PlayerWithTeamData(1, "P1", PlayerType.BATTER, 1, "Team", "TM")));
        when(fantasyPlayerConfigRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllPlayersWithConfig(leagueId);
        svc.getAllPlayersWithConfig(leagueId);

        verify(dao, times(1)).findPlayersWithTeamByLeagueId(leagueId);
    }

    @Test
    void reloadOverwritesCachedMatches() {
        stubMatchStore();
        MatchData first = sampleMatch(1);
        MatchData second = sampleMatch(2);
        when(dao.findAllMatches()).thenReturn(List.of(first)).thenReturn(List.of(second));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        assertEquals(1, svc.getAllMatchesWithTeams().getFirst().id());
        svc.reloadMatchesAndCachedPlayerLeagues();
        assertEquals(2, svc.getAllMatchesWithTeams().getFirst().id());

        verify(dao, times(2)).findAllMatches();
    }

    @Test
    void evictForcesNextReadFromDb() {
        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.evictAll();
        svc.getAllMatchesWithTeams();

        verify(dao, times(2)).findAllMatches();
    }

    @Test
    void matchHitAndMissRecordedInMicrometer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        MasterDataCacheMetrics metered = new MasterDataCacheMetrics(provider);

        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metered, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.getAllMatchesWithTeams();

        assertEquals(1.0, registry.counter("fantasy.master_cache.match.hits").count());
        assertEquals(1.0, registry.counter("fantasy.master_cache.match.misses").count());
    }

    @Test
    void playerHitAndMissRecordedInMicrometer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        MasterDataCacheMetrics metered = new MasterDataCacheMetrics(provider);

        stubMatchStore();
        stubLeaguePlayerStore();
        int leagueId = 3;
        when(dao.findPlayersWithTeamByLeagueId(leagueId)).thenReturn(List.of(
                new PlayerWithTeamData(1, "P1", PlayerType.BATTER, 1, "Team", "TM")));
        when(fantasyPlayerConfigRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metered, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllPlayersWithConfig(leagueId);
        svc.getAllPlayersWithConfig(leagueId);

        assertEquals(1.0, registry.counter("fantasy.master_cache.player.hits").count());
        assertEquals(1.0, registry.counter("fantasy.master_cache.player.misses").count());
    }

    @Test
    void enabledThirdListReadStillDoesNotHitDao() {
        stubMatchStore();
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllMatchesWithTeams();
        svc.getAllMatchesWithTeams();
        svc.getAllMatchesWithTeams();

        verify(dao, times(1)).findAllMatches();
    }

    @Test
    void listAfterSingleMatchByIdDoesNotCallFindAllMatches() {
        stubMatchStore();
        when(dao.findMatchById(5)).thenReturn(Optional.of(sampleMatch(5)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());
        when(dao.findTeamById(2)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getMatchById(5);
        verify(dao, never()).findAllMatches();

        List<MatchResponse> all = svc.getAllMatchesWithTeams();
        assertEquals(1, all.size());
        assertEquals(5, all.getFirst().id());
        verify(dao, never()).findAllMatches();
    }

    @Test
    void refreshMatchInCacheWritesWithoutFindAllMatches() {
        stubMatchStore();
        when(dao.findMatchById(3)).thenReturn(Optional.of(sampleMatch(3)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());
        when(dao.findTeamById(2)).thenReturn(java.util.Optional.empty());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.refreshMatchInCache(3);
        verify(dao, never()).findAllMatches();

        List<MatchResponse> all = svc.getAllMatchesWithTeams();
        assertEquals(1, all.size());
        verify(dao, never()).findAllMatches();
    }

    @Test
    void enabledThirdPlayerListReadStillSingleDaoLoad() {
        stubMatchStore();
        stubLeaguePlayerStore();
        int leagueId = 7;
        when(dao.findPlayersWithTeamByLeagueId(leagueId)).thenReturn(List.of(
                new PlayerWithTeamData(1, "P1", PlayerType.BATTER, 1, "Team", "TM")));
        when(fantasyPlayerConfigRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.getAllPlayersWithConfig(leagueId);
        svc.getAllPlayersWithConfig(leagueId);
        svc.getAllPlayersWithConfig(leagueId);

        verify(dao, times(1)).findPlayersWithTeamByLeagueId(leagueId);
    }

    @Test
    void reloadFillsPlayerStoresForConfiguredWarmLeagueIds() {
        stubMatchStore();
        stubLeaguePlayerStore();
        int leagueId = 2;
        when(dao.findAllMatches()).thenReturn(List.of(sampleMatch(1)));
        when(dao.findTeamById(1)).thenReturn(java.util.Optional.empty());
        when(dao.findPlayersWithTeamByLeagueId(leagueId)).thenReturn(List.of(
                new PlayerWithTeamData(10, "P1", PlayerType.BATTER, 1, "Team", "TM")));
        when(fantasyPlayerConfigRepository.findByLeagueId(leagueId)).thenReturn(List.of());

        MasterDataCacheService svc = new MasterDataCacheService(
                dao, mapper, fantasyPlayerConfigRepository, cacheStoreFactory, liveMasterCacheScope, metrics, true, false, "2");
        ReflectionTestUtils.invokeMethod(svc, "initStores");

        svc.reloadMatchesAndCachedPlayerLeagues();

        verify(dao, times(1)).findPlayersWithTeamByLeagueId(leagueId);
        assertEquals(1, svc.getAllPlayersWithConfig(leagueId).size());
        verify(dao, times(1)).findPlayersWithTeamByLeagueId(leagueId);
    }

    private static MatchData sampleMatch(int id) {
        return new MatchData(id, java.time.LocalDate.now(), false, "UPCOMING", "M1", null,
                java.time.LocalTime.NOON, null, "venue", "toss", 1, null, 1, 2);
    }
}
