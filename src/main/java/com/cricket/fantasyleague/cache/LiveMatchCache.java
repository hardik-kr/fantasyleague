package com.cricket.fantasyleague.cache;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.payload.fullscorecarddto.FullScorecardDto;
import com.cricket.fantasyleague.repository.PlayerRepository;
import com.cricket.fantasyleague.service.MatchService;
import com.cricket.fantasyleague.util.AppConstants;

/**
 * Centralized in-memory cache for live match processing.
 * Eliminates redundant HTTP calls and DB queries during each polling cycle.
 *
 * TTLs are tuned for live-match polling (~60s intervals):
 *   - Scorecard:       25s  (fresh data each cycle, avoids double-fetch within a cycle)
 *   - Today's matches: 2min (match list is stable within a day)
 *   - Team players:    30min(roster changes are rare during a tournament)
 */
@Service
public class LiveMatchCache {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchCache.class);

    private static final long SCORECARD_TTL_MS = 25_000;
    private static final long TODAY_MATCHES_TTL_MS = 120_000;
    private static final long TEAM_PLAYERS_TTL_MS = 1_800_000;

    private final RestTemplate restTemplate;
    private final MatchService matchService;
    private final PlayerRepository playerRepository;

    private final ConcurrentHashMap<Integer, CachedEntry<FullScorecardDto>> scorecardCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<PlayerPoints>> playerPointsRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<List<Player>>> teamPlayersCache = new ConcurrentHashMap<>();
    private volatile CachedEntry<List<Match>> todayMatchesEntry;

    public LiveMatchCache(RestTemplate restTemplate,
                          MatchService matchService,
                          PlayerRepository playerRepository) {
        this.restTemplate = restTemplate;
        this.matchService = matchService;
        this.playerRepository = playerRepository;
    }

    // ── Scorecard (from Cricket API via HTTP) ──

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
                AppConstants.URI.SCORECARD_FULL + matchId, FullScorecardDto.class);

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

    // ── Today's Matches (from DB, cached) ──

    public List<Match> getTodayMatches() {
        CachedEntry<List<Match>> entry = todayMatchesEntry;
        if (entry != null && !entry.isExpired(TODAY_MATCHES_TTL_MS)) {
            return entry.value;
        }

        List<Match> matches = matchService.findMatchByDate(LocalDate.now());
        todayMatchesEntry = new CachedEntry<>(matches);
        return matches;
    }

    // ── Team Players (from DB, cached) ──

    public List<Player> getTeamPlayers(String teamName) {
        CachedEntry<List<Player>> entry = teamPlayersCache.get(teamName);
        if (entry != null && !entry.isExpired(TEAM_PLAYERS_TTL_MS)) {
            return entry.value;
        }

        List<Player> players = playerRepository.findByTeamidName(teamName);
        teamPlayersCache.put(teamName, new CachedEntry<>(players));
        return players;
    }

    // ── PlayerPoints Records (managed externally by PlayerPointsServiceImpl) ──

    public List<PlayerPoints> getPlayerPointsRecords(Integer matchId) {
        return playerPointsRecords.get(matchId);
    }

    public void putPlayerPointsRecords(Integer matchId, List<PlayerPoints> records) {
        playerPointsRecords.put(matchId, records);
    }

    // ── Eviction ──

    public void evictMatch(Integer matchId) {
        scorecardCache.remove(matchId);
        playerPointsRecords.remove(matchId);
    }

    public void evictAll() {
        scorecardCache.clear();
        playerPointsRecords.clear();
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
