package com.cricket.fantasyleague.service.masterdata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.store.CacheStore;
import com.cricket.fantasyleague.cache.store.CacheStoreFactory;
import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.payload.response.MatchResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.response.TeamBrief;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;

import jakarta.annotation.PostConstruct;

/**
 * Master match/player cache: one Redis/in-memory entry per match id and per player id
 * within a league namespace ({@code fantasy:master:matches:v1}, {@code fantasy:master:players:league:&lt;id&gt;:v1}).
 */
@Service
public class MasterDataCacheService implements MasterDataReadService {

    private static final Logger logger = LoggerFactory.getLogger(MasterDataCacheService.class);
    private static final String MATCH_NAMESPACE = "master:matches:v1";

    private final CricketMasterDataDao dao;
    private final CricketEntityMapper mapper;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final CacheStoreFactory cacheStoreFactory;
    private final LiveMasterCacheScope liveMasterCacheScope;
    private final MasterDataCacheMetrics metrics;

    /** When false, reads bypass stores and hit the DB every time ({@code fantasy.master-cache.enabled}). */
    private final boolean masterCacheEnabled;

    /** Option C: warm match + configured league player hashes once at startup ({@code fantasy.master-cache.warm-on-startup}). */
    private final boolean warmOnStartup;

    /** League ids from {@code fantasy.master-cache.warm-player-league-ids} (comma-separated); reload fills each from DB. */
    private final List<Integer> warmPlayerLeagueIds;

    private final ReentrantLock matchLoadLock = new ReentrantLock();
    private final ReentrantLock playerLoadLock = new ReentrantLock();
    private final ReentrantLock refreshLock = new ReentrantLock();

    private CacheStore<Integer, MatchResponse> matchStore;
    private final ConcurrentHashMap<Integer, CacheStore<Integer, PlayerResponse>> playerStoresByLeague =
            new ConcurrentHashMap<>();

    /** True after a full list load, reload, or non-empty hash treated as authoritative (invalidated by evict only). */
    private volatile boolean matchCatalogComplete;

    /** Leagues whose player hash holds a full roster snapshot from the last successful list load or reload. */
    private final ConcurrentHashMap<Integer, Boolean> playerLeagueCatalogComplete = new ConcurrentHashMap<>();

    public MasterDataCacheService(CricketMasterDataDao dao,
                                  CricketEntityMapper mapper,
                                  FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                                  CacheStoreFactory cacheStoreFactory,
                                  LiveMasterCacheScope liveMasterCacheScope,
                                  MasterDataCacheMetrics metrics,
                                  @Value("${fantasy.master-cache.enabled:true}") boolean masterCacheEnabled,
                                  @Value("${fantasy.master-cache.warm-on-startup:false}") boolean warmOnStartup,
                                  @Value("${fantasy.master-cache.warm-player-league-ids:}") String warmPlayerLeagueIdsCsv) {
        this.dao = dao;
        this.mapper = mapper;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.cacheStoreFactory = cacheStoreFactory;
        this.liveMasterCacheScope = liveMasterCacheScope;
        this.metrics = metrics;
        this.masterCacheEnabled = masterCacheEnabled;
        this.warmOnStartup = warmOnStartup;
        this.warmPlayerLeagueIds = parseWarmPlayerLeagueIds(warmPlayerLeagueIdsCsv);
    }

    private static List<Integer> parseWarmPlayerLeagueIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .toList();
    }

    @PostConstruct
    void initStores() {
        this.matchStore = cacheStoreFactory.create(MATCH_NAMESPACE, Integer.class, MatchResponse.class);
        if (!masterCacheEnabled) {
            logger.info("fantasy.master-cache.enabled=false — master match/player cache reads bypass store");
        } else {
            logger.info("fantasy.master-cache.enabled=true — strategy={}", cacheStoreFactory.isRedis() ? "redis" : "in_memory");
            if (!warmPlayerLeagueIds.isEmpty()) {
                logger.info("fantasy.master-cache.warm-player-league-ids — will reload players for league id(s): {}",
                        warmPlayerLeagueIds);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void warmMasterCacheOnStartup() {
        if (!masterCacheEnabled || !warmOnStartup) {
            return;
        }
        try {
            reloadMatchesAndCachedPlayerLeagues();
            logger.info("fantasy.master-cache.warm-on-startup=true — master cache warmed");
        } catch (Exception e) {
            logger.warn("Master cache startup warm failed", e);
        }
    }

    private CacheStore<Integer, PlayerResponse> leaguePlayerStore(int leagueId) {
        return playerStoresByLeague.computeIfAbsent(leagueId, id ->
                cacheStoreFactory.create("master:players:league:" + id + ":v1", Integer.class, PlayerResponse.class));
    }

    @Override
    public boolean isEnabled() {
        return masterCacheEnabled;
    }

    @Override
    public List<MatchResponse> getAllMatchesWithTeams() {
        if (!masterCacheEnabled) {
            return loadMatchesFromDb();
        }
        matchLoadLock.lock();
        try {
            if (matchCatalogComplete && matchStore.size() > 0) {
                metrics.onMatchHit();
                return snapshotMatchesSorted();
            }
            if (matchStore.size() > 0) {
                matchCatalogComplete = true;
                metrics.onMatchHit();
                return snapshotMatchesSorted();
            }
            // Cold empty store — policy A: one DB load fills the hash; later lists are cache-only.
            List<MatchData> rows = dao.findAllMatches();
            if (rows.isEmpty()) {
                matchStore.clear();
                matchCatalogComplete = true;
                return List.of();
            }
            List<MatchResponse> fresh = buildMatchResponses(rows);
            matchStore.clear();
            for (MatchResponse m : fresh) {
                matchStore.put(m.id(), m);
            }
            matchCatalogComplete = true;
            metrics.onMatchMiss();
            return fresh;
        } finally {
            matchLoadLock.unlock();
        }
    }

    private List<MatchResponse> snapshotMatchesSorted() {
        List<MatchResponse> all = new ArrayList<>(matchStore.asMap().values());
        all.sort(Comparator.comparing(MatchResponse::id));
        return all;
    }

    @Override
    public Optional<MatchResponse> getMatchById(Integer matchId) {
        if (matchId == null) {
            return Optional.empty();
        }
        if (!masterCacheEnabled) {
            return loadMatchResponseById(matchId);
        }
        matchLoadLock.lock();
        try {
            MatchResponse cached = matchStore.get(matchId);
            if (cached != null) {
                metrics.onMatchHit();
                return Optional.of(cached);
            }
            Optional<MatchResponse> fresh = loadMatchResponseById(matchId);
            if (fresh.isPresent()) {
                matchStore.put(matchId, fresh.get());
            }
            metrics.onMatchMiss();
            return fresh;
        } finally {
            matchLoadLock.unlock();
        }
    }

    @Override
    public List<PlayerResponse> getAllPlayersWithConfig(Integer leagueId) {
        if (!masterCacheEnabled) {
            return loadPlayersFromDb(leagueId);
        }
        CacheStore<Integer, PlayerResponse> ps = leaguePlayerStore(leagueId);
        playerLoadLock.lock();
        try {
            if (Boolean.TRUE.equals(playerLeagueCatalogComplete.get(leagueId)) && ps.size() > 0) {
                metrics.onPlayerHit();
                return snapshotPlayersSorted(ps);
            }
            if (ps.size() > 0) {
                playerLeagueCatalogComplete.put(leagueId, true);
                metrics.onPlayerHit();
                return snapshotPlayersSorted(ps);
            }
            List<PlayerWithTeamData> rows = dao.findPlayersWithTeamByLeagueId(leagueId);
            if (rows.isEmpty()) {
                ps.clear();
                playerLeagueCatalogComplete.put(leagueId, true);
                return List.of();
            }
            List<PlayerResponse> fresh = buildPlayersForLeague(rows, leagueId);
            ps.clear();
            for (PlayerResponse p : fresh) {
                ps.put(p.id(), p);
            }
            playerLeagueCatalogComplete.put(leagueId, true);
            metrics.onPlayerMiss();
            return fresh;
        } finally {
            playerLoadLock.unlock();
        }
    }

    private List<PlayerResponse> snapshotPlayersSorted(CacheStore<Integer, PlayerResponse> ps) {
        List<PlayerResponse> all = new ArrayList<>(ps.asMap().values());
        all.sort(Comparator.comparing(PlayerResponse::id));
        return all;
    }

    @Override
    public Optional<PlayerResponse> getPlayerWithConfig(Integer leagueId, Integer playerId) {
        if (leagueId == null || playerId == null) {
            return Optional.empty();
        }
        if (!masterCacheEnabled) {
            return findPlayerInLeagueFromDb(leagueId, playerId);
        }
        CacheStore<Integer, PlayerResponse> ps = leaguePlayerStore(leagueId);
        playerLoadLock.lock();
        try {
            PlayerResponse cached = ps.get(playerId);
            if (cached != null) {
                metrics.onPlayerHit();
                return Optional.of(cached);
            }
            Optional<PlayerResponse> fresh = findPlayerInLeagueFromDb(leagueId, playerId);
            if (fresh.isPresent()) {
                ps.put(playerId, fresh.get());
            }
            metrics.onPlayerMiss();
            return fresh;
        } finally {
            playerLoadLock.unlock();
        }
    }

    private Optional<PlayerResponse> findPlayerInLeagueFromDb(Integer leagueId, Integer playerId) {
        return loadPlayersFromDb(leagueId).stream()
                .filter(p -> playerId.equals(p.id()))
                .findFirst();
    }

    /** Replaces one league's player hash from DB; caller must hold {@link #playerLoadLock}. */
    private void refillPlayerLeagueStoreFromDb(Integer leagueId) {
        CacheStore<Integer, PlayerResponse> ps = leaguePlayerStore(leagueId);
        ps.clear();
        for (PlayerResponse p : loadPlayersFromDb(leagueId)) {
            ps.put(p.id(), p);
        }
        playerLeagueCatalogComplete.put(leagueId, true);
    }

    @Override
    public void reloadMatchesAndCachedPlayerLeagues() {
        if (!masterCacheEnabled) {
            return;
        }
        refreshLock.lock();
        try {
            matchLoadLock.lock();
            try {
                List<MatchResponse> matches = loadMatchesFromDb();
                matchStore.clear();
                for (MatchResponse m : matches) {
                    matchStore.put(m.id(), m);
                }
                matchCatalogComplete = true;
            } finally {
                matchLoadLock.unlock();
            }
            playerLoadLock.lock();
            try {
                if (warmPlayerLeagueIds.isEmpty()) {
                    logger.debug("Master data cache: reloaded matches; warm-player-league-ids empty — no player warm");
                } else {
                    for (Integer leagueId : warmPlayerLeagueIds) {
                        if (leagueId == null) {
                            continue;
                        }
                        refillPlayerLeagueStoreFromDb(leagueId);
                    }
                    logger.debug("Master data cache: reloaded matches + {} configured player league store(s)",
                            warmPlayerLeagueIds.size());
                }
            } finally {
                playerLoadLock.unlock();
            }
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public void evictAll() {
        if (!masterCacheEnabled) {
            return;
        }
        refreshLock.lock();
        try {
            matchLoadLock.lock();
            try {
                matchStore.clear();
                matchCatalogComplete = false;
            } finally {
                matchLoadLock.unlock();
            }
            playerLoadLock.lock();
            try {
                for (CacheStore<Integer, PlayerResponse> ps : playerStoresByLeague.values()) {
                    ps.clear();
                }
                playerStoresByLeague.clear();
                playerLeagueCatalogComplete.clear();
            } finally {
                playerLoadLock.unlock();
            }
            liveMasterCacheScope.clear();
            logger.info("Master data cache: evicted all");
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public void refreshIfLiveMatchActive() {
        if (!masterCacheEnabled) {
            return;
        }
        LiveMasterSnapshot snap = liveMasterCacheScope.get();
        if (snap == null) {
            return;
        }
        refreshMatchInCache(snap.matchId());
        refreshPlayersInMasterCache(snap.leagueId(), snap.playerIds());
    }

    /**
     * Refreshes only the given player ids in the league master hash (HSET each field). Caller should hold
     * no master locks; this method takes {@link #playerLoadLock} only.
     */
    void refreshPlayersInMasterCache(int leagueId, int[] playerIds) {
        if (!masterCacheEnabled || playerIds == null || playerIds.length == 0) {
            return;
        }
        List<Integer> idList = new ArrayList<>(playerIds.length);
        for (int pid : playerIds) {
            idList.add(pid);
        }
        playerLoadLock.lock();
        try {
            List<PlayerWithTeamData> rows = dao.findPlayersWithTeamByLeagueIdAndPlayerIds(leagueId, idList);
            if (rows.isEmpty()) {
                return;
            }
            List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueIdAndPlayerIdIn(leagueId, idList);
            Map<Integer, FantasyPlayerConfig> configMap = new HashMap<>(configs.size());
            for (FantasyPlayerConfig cfg : configs) {
                configMap.put(cfg.getPlayerId(), cfg);
            }
            CacheStore<Integer, PlayerResponse> ps = leaguePlayerStore(leagueId);
            for (PlayerWithTeamData p : rows) {
                ps.put(p.id(), toPlayerResponse(p, configMap.get(p.id())));
            }
        } finally {
            playerLoadLock.unlock();
        }
    }

    private static PlayerResponse toPlayerResponse(PlayerWithTeamData p, FantasyPlayerConfig cfg) {
        return new PlayerResponse(
                p.id(), p.name(), p.role(),
                p.teamId(), p.teamName(), p.teamShortName(),
                cfg != null ? cfg.getCredit() : null,
                cfg != null ? cfg.getOverseas() : false,
                cfg != null ? cfg.getUncapped() : false,
                cfg != null ? cfg.getTotalPoints() : 0.0,
                cfg != null ? cfg.getIsActive() : true
        );
    }

    @Override
    public void refreshMatchInCache(Integer matchId) {
        if (!masterCacheEnabled || matchId == null) {
            return;
        }
        matchLoadLock.lock();
        try {
            Optional<MatchResponse> fresh = loadMatchResponseById(matchId);
            if (fresh.isPresent()) {
                matchStore.put(matchId, fresh.get());
            }
        } finally {
            matchLoadLock.unlock();
        }
    }

    private Optional<MatchResponse> loadMatchResponseById(Integer matchId) {
        return dao.findMatchById(matchId).map(md -> toMatchResponse(md, new HashMap<>()));
    }

    private MatchResponse toMatchResponse(MatchData md, Map<Integer, TeamBrief> teamCache) {
        return new MatchResponse(
                md.id(), md.date(), md.time(), md.venue(),
                md.toss(), md.result(), md.isMatchComplete(),
                md.matchState(), md.matchDesc(),
                resolveTeam(md.teamAId(), teamCache),
                resolveTeam(md.teamBId(), teamCache)
        );
    }

    private List<MatchResponse> loadMatchesFromDb() {
        return buildMatchResponses(dao.findAllMatches());
    }

    private List<MatchResponse> buildMatchResponses(List<MatchData> allMatches) {
        Map<Integer, TeamBrief> teamCache = new HashMap<>();
        List<MatchResponse> result = new ArrayList<>(allMatches.size());
        for (MatchData md : allMatches) {
            result.add(toMatchResponse(md, teamCache));
        }
        return result;
    }

    private TeamBrief resolveTeam(Integer teamId, Map<Integer, TeamBrief> cache) {
        if (teamId == null) {
            return null;
        }
        return cache.computeIfAbsent(teamId, id -> {
            Team t = dao.findTeamById(id).map(mapper::toTeam).orElse(null);
            if (t == null) {
                return new TeamBrief(id, null, null);
            }
            return new TeamBrief(t.getId(), t.getName(), t.getShortName());
        });
    }

    private List<PlayerResponse> loadPlayersFromDb(Integer leagueId) {
        List<PlayerWithTeamData> players = dao.findPlayersWithTeamByLeagueId(leagueId);
        return buildPlayersForLeague(players, leagueId);
    }

    private List<PlayerResponse> buildPlayersForLeague(List<PlayerWithTeamData> players, Integer leagueId) {
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);

        Map<Integer, FantasyPlayerConfig> configMap = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            configMap.put(cfg.getPlayerId(), cfg);
        }

        List<PlayerResponse> result = new ArrayList<>(players.size());
        for (PlayerWithTeamData p : players) {
            result.add(toPlayerResponse(p, configMap.get(p.id())));
        }
        return result;
    }
}
