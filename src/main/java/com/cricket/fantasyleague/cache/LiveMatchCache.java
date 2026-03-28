package com.cricket.fantasyleague.cache;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.payload.fullscorecarddto.FullScorecardDto;
import com.cricket.fantasyleague.service.match.MatchService;

@Service
public class LiveMatchCache {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchCache.class);

    private static final long SCORECARD_TTL_MS = 25_000;
    private static final long TODAY_MATCHES_TTL_MS = 120_000;
    private static final long TEAM_PLAYERS_TTL_MS = 1_800_000;

    private final RestTemplate restTemplate;
    private final MatchService matchService;
    private final CricketMasterDataDao dao;
    private final CricketEntityMapper cricketEntities;
    private final String cricketScorecardFullUrlPrefix;

    private final ConcurrentHashMap<Integer, CachedEntry<FullScorecardDto>> scorecardCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<PlayerPoints>> playerPointsRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<List<Player>>> teamPlayersCache = new ConcurrentHashMap<>();
    private final Set<Integer> dirtyPlayerPointsMatchIds = ConcurrentHashMap.newKeySet();
    private volatile CachedEntry<List<Match>> todayMatchesEntry;

    public LiveMatchCache(RestTemplate restTemplate,
                          MatchService matchService,
                          CricketMasterDataDao dao,
                          CricketEntityMapper cricketEntities,
                          @Value("${cricketapi.base-url:http://localhost:9090}") String cricketApiBaseUrl) {
        this.restTemplate = restTemplate;
        this.matchService = matchService;
        this.dao = dao;
        this.cricketEntities = cricketEntities;
        String base = cricketApiBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.cricketScorecardFullUrlPrefix = base + "/scorecard/full/";
    }

    public FullScorecardDto getScorecard(Integer matchId) {
        CachedEntry<FullScorecardDto> entry = scorecardCache.get(matchId);
        if (entry != null && !entry.isExpired(SCORECARD_TTL_MS)) {
            logger.debug("Scorecard cache HIT for match {}", matchId);
            return entry.value;
        }

        logger.debug("Scorecard cache MISS for match {}, fetching from Cricket API", matchId);
        FullScorecardDto scorecard = fetchScorecardFromApi(matchId);
        scorecardCache.put(matchId, new CachedEntry<>(scorecard));
        return scorecard;
    }

    private FullScorecardDto fetchScorecardFromApi(Integer matchId) {
        ResponseEntity<FullScorecardDto> response = restTemplate.getForEntity(
                cricketScorecardFullUrlPrefix + matchId, FullScorecardDto.class);

        if (response.getStatusCode() != HttpStatus.OK
                && response.getStatusCode() != HttpStatus.ACCEPTED) {
            throw new CommonException("Error fetching scorecard for match " + matchId);
        }

        FullScorecardDto body = response.getBody();
        if (body == null) {
            throw new CommonException("Null scorecard response for match " + matchId);
        }
        return body;
    }

    public List<Match> getTodayMatches() {
        CachedEntry<List<Match>> entry = todayMatchesEntry;
        if (entry != null && !entry.isExpired(TODAY_MATCHES_TTL_MS)) {
            return entry.value;
        }

        List<Match> matches = matchService.findMatchByDate(nowDate());
        todayMatchesEntry = new CachedEntry<>(matches);
        return matches;
    }

    public List<Player> getTeamPlayers(String teamName) {
        CachedEntry<List<Player>> entry = teamPlayersCache.get(teamName);
        if (entry != null && !entry.isExpired(TEAM_PLAYERS_TTL_MS)) {
            return entry.value;
        }

        List<Player> players = dao.findPlayersByTeamName(teamName).stream()
                .map(cricketEntities::toPlayer)
                .filter(p -> p != null)
                .toList();
        teamPlayersCache.put(teamName, new CachedEntry<>(players));
        return players;
    }

    public List<PlayerPoints> getPlayerPointsRecords(Integer matchId) {
        return playerPointsRecords.get(matchId);
    }

    public void putPlayerPointsRecords(Integer matchId, List<PlayerPoints> records) {
        playerPointsRecords.put(matchId, records);
    }

    public void markPlayerPointsDirty(Integer matchId) {
        dirtyPlayerPointsMatchIds.add(matchId);
    }

    public boolean isDirtyPlayerPoints(Integer matchId) {
        return dirtyPlayerPointsMatchIds.contains(matchId);
    }

    public void clearPlayerPointsDirty(Integer matchId) {
        dirtyPlayerPointsMatchIds.remove(matchId);
    }

    public void evictMatch(Integer matchId) {
        scorecardCache.remove(matchId);
        playerPointsRecords.remove(matchId);
        dirtyPlayerPointsMatchIds.remove(matchId);
    }

    public void evictAll() {
        scorecardCache.clear();
        playerPointsRecords.clear();
        dirtyPlayerPointsMatchIds.clear();
        teamPlayersCache.clear();
        todayMatchesEntry = null;
    }

    private record CachedEntry<T>(T value, long createdAt) {
        CachedEntry(T value) {
            this(value, System.currentTimeMillis());
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }
}
