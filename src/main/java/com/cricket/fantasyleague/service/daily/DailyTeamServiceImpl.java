package com.cricket.fantasyleague.service.daily;

import static com.cricket.fantasyleague.util.MatchTimeUtils.nowDateTime;
import static com.cricket.fantasyleague.util.MatchTimeUtils.toIST;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.cache.DailyLiveMatchTeamCache;
import com.cricket.fantasyleague.cache.dto.CachedDailyUserMatchTeam;
import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.PlayerWithTeamData;
import com.cricket.fantasyleague.entity.enums.MatchState;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeamDraft;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.daily.DailyDraftResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchHistoryResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchPlayerPoolResponse;
import com.cricket.fantasyleague.payload.daily.DailyMatchSummary;
import com.cricket.fantasyleague.payload.daily.DailyMyTeamOverview;
import com.cricket.fantasyleague.payload.daily.DailyTeamRequest;
import com.cricket.fantasyleague.payload.daily.DailyTeamResponse;
import com.cricket.fantasyleague.payload.response.PlayerBrief;
import com.cricket.fantasyleague.payload.response.PlayerDetailResponse;
import com.cricket.fantasyleague.payload.response.PlayerResponse;
import com.cricket.fantasyleague.payload.response.TeamBrief;
import com.cricket.fantasyleague.repository.FantasyPlayerConfigRepository;
import com.cricket.fantasyleague.repository.PlayerPointsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamDraftRepository;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamRepository;
import com.cricket.fantasyleague.service.match.MatchService;

/**
 * Daily Challenge orchestration: upcoming-match listing, per-match player pool,
 * draft upsert, locked-team retrieval and user history.
 *
 * <p>The {@link DailyTeamValidator} is the single source of composition rules;
 * this service is responsible for fetching the per-match pool, persisting the
 * draft, and assembling response payloads. Lock-window checks reuse the same
 * semantics as the season-long flow ({@code findLockedMatch} + the configured
 * lock window minutes).
 */
@Service
public class DailyTeamServiceImpl implements DailyTeamService {

    private static final Logger logger = LoggerFactory.getLogger(DailyTeamServiceImpl.class);

    private final DailyUserMatchTeamRepository teamRepo;
    private final DailyUserMatchTeamDraftRepository draftRepo;
    private final DailyTeamValidator validator;
    private final CricketMasterDataDao cricketDao;
    private final CricketEntityMapper cricketEntities;
    private final FantasyPlayerConfigRepository fantasyPlayerConfigRepository;
    private final PlayerPointsRepository playerPointsRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;
    private final DailyMetrics metrics;
    private final DailyLiveMatchTeamCache liveCache;

    public DailyTeamServiceImpl(DailyUserMatchTeamRepository teamRepo,
                                DailyUserMatchTeamDraftRepository draftRepo,
                                DailyTeamValidator validator,
                                CricketMasterDataDao cricketDao,
                                CricketEntityMapper cricketEntities,
                                FantasyPlayerConfigRepository fantasyPlayerConfigRepository,
                                PlayerPointsRepository playerPointsRepository,
                                UserRepository userRepository,
                                MatchService matchService,
                                DailyMetrics metrics,
                                DailyLiveMatchTeamCache liveCache) {
        this.teamRepo = teamRepo;
        this.draftRepo = draftRepo;
        this.validator = validator;
        this.cricketDao = cricketDao;
        this.cricketEntities = cricketEntities;
        this.fantasyPlayerConfigRepository = fantasyPlayerConfigRepository;
        this.playerPointsRepository = playerPointsRepository;
        this.userRepository = userRepository;
        this.matchService = matchService;
        this.metrics = metrics;
        this.liveCache = liveCache;
    }

    @Override
    public DailyMatchSummary getNextMatch(User user) {
        Match m = matchService.findNextUpcomingMatch();
        if (m == null || m.getDate() == null || m.getTime() == null) return null;
        LocalDateTime now = nowDateTime();
        if (!toIST(m.getDate(), m.getTime(), m.getTimezone()).isAfter(now)) {
            // already started — daily editing is closed; nothing to surface as "next"
            return null;
        }
        boolean hasDraft = draftRepo.findByMatchAndUser(m, user).isPresent();
        boolean hasLocked = teamRepo.existsByMatchAndUser(m, user);
        boolean locked = isMatchLocked(m, now);
        return new DailyMatchSummary(
                m.getId(), m.getDate(), m.getTime(), m.getMatchDesc(), m.getVenue(),
                toBrief(m.getTeamA()), toBrief(m.getTeamB()),
                hasDraft, hasLocked, locked);
    }

    @Override
    public DailyMatchPlayerPoolResponse getActivePlayerPool() {
        Match match = requireActiveDailyMatch();
        Integer teamAId = match.getTeamA() != null ? match.getTeamA().getId() : null;
        Integer teamBId = match.getTeamB() != null ? match.getTeamB().getId() : null;
        if (teamAId == null || teamBId == null) {
            throw new CommonException("Match " + match.getId() + " has no teams assigned yet");
        }

        List<PlayerWithTeamData> pool = cricketDao.findPlayersWithTeamByTeamIds(List.of(teamAId, teamBId));

        Map<Integer, FantasyPlayerConfig> configMap = buildConfigMap(match.getLeagueId());

        List<PlayerResponse> players = new ArrayList<>(pool.size());
        for (PlayerWithTeamData p : pool) {
            FantasyPlayerConfig cfg = configMap.get(p.id());
            players.add(new PlayerResponse(
                    p.id(), p.name(), p.role(),
                    p.teamId(), p.teamName(), p.teamShortName(),
                    cfg != null ? cfg.getCredit() : null,
                    cfg != null ? cfg.getOverseas() : false,
                    cfg != null ? cfg.getUncapped() : false,
                    cfg != null ? cfg.getTotalPoints() : 0.0,
                    cfg != null ? cfg.getIsActive() : true));
        }

        return new DailyMatchPlayerPoolResponse(match.getId(),
                toBrief(match.getTeamA()), toBrief(match.getTeamB()), players);
    }

    @Override
    public DailyDraftResponse getActiveDraft(User user) {
        Match match = requireActiveDailyMatch();
        DailyUserMatchTeamDraft draft = draftRepo.findByMatchAndUser(match, user).orElse(null);
        boolean locked = isMatchLocked(match, nowDateTime());

        String teamA = match.getTeamA() != null ? match.getTeamA().getShortName() : null;
        String teamB = match.getTeamB() != null ? match.getTeamB().getShortName() : null;

        if (draft == null) {
            return new DailyDraftResponse(null,
                    match.getId(), match.getDate(), match.getTime(), match.getMatchDesc(),
                    teamA, teamB, false, null, null, List.of(), locked);
        }

        List<PlayerBrief> playing11 = resolvePlayerBriefs(draft.getPlaying11(), buildPlayerTeamMap(match));
        return new DailyDraftResponse(null,
                match.getId(), match.getDate(), match.getTime(), match.getMatchDesc(),
                teamA, teamB, true,
                draft.getCaptainId(), draft.getViceCaptainId(),
                playing11, locked);
    }

    @Override
    @Transactional
    public void upsertDraft(User user, DailyTeamRequest request) {
        Match match = requireActiveDailyMatch();
        if (teamRepo.existsByMatchAndUser(match, user)) {
            // already promoted to locked team — drafts no longer apply
            throw new CommonException("Your team for this match is already locked.");
        }

        Integer teamAId = match.getTeamA() != null ? match.getTeamA().getId() : null;
        Integer teamBId = match.getTeamB() != null ? match.getTeamB().getId() : null;
        if (teamAId == null || teamBId == null) {
            throw new CommonException("Match " + match.getId() + " has no teams assigned yet");
        }

        Set<Integer> matchPool = collectMatchPool(teamAId, teamBId);
        List<Player> players = loadPlayers(request.getPlaying11());
        Map<Integer, FantasyPlayerConfig> configMap = buildConfigMap(match.getLeagueId());

        validator.validate(request, players, matchPool, configMap);

        DailyUserMatchTeamDraft draft = draftRepo.findByMatchAndUser(match, user).orElse(null);
        if (draft == null) {
            draft = new DailyUserMatchTeamDraft(user, match,
                    request.getCaptainId(), request.getViceCaptainId(),
                    new ArrayList<>(request.getPlaying11()));
        } else {
            draft.setCaptainId(request.getCaptainId());
            draft.setViceCaptainId(request.getViceCaptainId());
            if (draft.getPlaying11() != null) {
                draft.getPlaying11().clear();
                draft.getPlaying11().addAll(request.getPlaying11());
            } else {
                draft.setPlaying11(new ArrayList<>(request.getPlaying11()));
            }
        }
        draftRepo.save(draft);
        metrics.onDraftUpsert();
        logger.info("Daily draft saved: matchId={}, userId={}", match.getId(), user.getId());
    }

    /**
     * View a (user, match) pair's locked daily team. Mirrors the season-long
     * {@code UserTeamServiceImpl#getUserTeamForMatch} contract — same
     * validation order, same {@code found=false} fallthrough — so the daily
     * surface stays uniform with {@code POST /api/seasons/team}.
     */
    @Override
    public DailyTeamResponse getMyTeam(Long userId, Integer matchId) {
        if (userId == null || matchId == null) {
            return new DailyTeamResponse(false, "userId and matchId are required",
                    null, null, null, null, null, null, null, List.of());
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        Match match = loadMatch(matchId);

        // Hot path: when the match is currently live, the cache holds the
        // freshest match_points for every locked team — DB rows are stale
        // for up to one flush interval. Build the response from the cached
        // DTO and skip the per-call findByMatchAndUser DB hit.
        if (liveCache.isWarmedUp(matchId)) {
            DailyLiveMatchTeamCache.CachedTeamWithRank cached = liveCache.getByUserWithRank(matchId, userId);
            if (cached == null) {
                metrics.onCacheMiss();
                return new DailyTeamResponse(false, "No locked daily team for this user/match",
                        user.getId(), user.getUsername(), user.getFirstname(),
                        matchId, null, null, null, List.of());
            }
            metrics.onCacheHit();
            return buildTeamResponseFromCache(user, match, cached.team());
        }

        metrics.onCacheMiss();
        DailyUserMatchTeam team = teamRepo.findByMatchAndUser(match, user).orElse(null);
        if (team == null) {
            return new DailyTeamResponse(false, "No locked daily team for this user/match",
                    user.getId(), user.getUsername(), user.getFirstname(),
                    matchId, null, null, null, List.of());
        }

        Map<Integer, Double> ppMap = new HashMap<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }

        List<PlayerDetailResponse> playing11 = new ArrayList<>();
        if (team.getPlaying11() != null && !team.getPlaying11().isEmpty()) {
            List<PlayerData> players = cricketDao.findPlayersByIds(new ArrayList<>(team.getPlaying11()));
            Map<Integer, PlayerData> byId = new HashMap<>();
            for (PlayerData pd : players) byId.put(pd.id(), pd);
            Map<Integer, String> playerTeamMap = buildPlayerTeamMap(match);

            for (Integer pid : team.getPlaying11()) {
                PlayerData pd = byId.get(pid);
                String name = pd != null ? pd.name() : null;
                String role = pd != null && pd.role() != null ? pd.role().name() : null;
                String tag = resolveTag(pid, team);
                playing11.add(new PlayerDetailResponse(pid, name, role,
                        ppMap.getOrDefault(pid, 0.0), tag, playerTeamMap.get(pid)));
            }
        }

        return new DailyTeamResponse(true, null,
                user.getId(), user.getUsername(), user.getFirstname(),
                matchId, team.getMatchPoints(),
                team.getCaptainId(), team.getViceCaptainId(),
                playing11);
    }

    /**
     * Render a {@link DailyTeamResponse} from a cached team DTO. Player
     * names/roles and the team-shortName map still need a one-shot lookup
     * via {@link CricketMasterDataDao} (master data isn't in the live cache),
     * but the team's match_points + captain/vc and the player_points map
     * come from the cache and the per-match player-points read — both of
     * which are cheap reads that avoid touching {@code daily_user_match_team}.
     */
    private DailyTeamResponse buildTeamResponseFromCache(User user, Match match, CachedDailyUserMatchTeam cached) {
        Integer matchId = match != null ? match.getId() : cached.matchId();

        Map<Integer, Double> ppMap = new HashMap<>();
        if (matchId != null) {
            for (PlayerPoints pp : playerPointsRepository.findByMatchId(matchId)) {
                ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
            }
        }

        List<PlayerDetailResponse> playing11 = new ArrayList<>();
        List<Integer> ids = cached.playing11Ids();
        if (ids != null && !ids.isEmpty()) {
            List<PlayerData> players = cricketDao.findPlayersByIds(new ArrayList<>(ids));
            Map<Integer, PlayerData> byId = new HashMap<>();
            for (PlayerData pd : players) byId.put(pd.id(), pd);
            Map<Integer, String> playerTeamMap = buildPlayerTeamMap(match);

            for (Integer pid : ids) {
                PlayerData pd = byId.get(pid);
                String name = pd != null ? pd.name() : null;
                String role = pd != null && pd.role() != null ? pd.role().name() : null;
                String tag = resolveTagFromCache(pid, cached);
                playing11.add(new PlayerDetailResponse(pid, name, role,
                        ppMap.getOrDefault(pid, 0.0), tag, playerTeamMap.get(pid)));
            }
        }

        return new DailyTeamResponse(true, null,
                user.getId(), user.getUsername(), user.getFirstname(),
                matchId, cached.matchPoints(),
                cached.captainId(), cached.viceCaptainId(),
                playing11);
    }

    private String resolveTagFromCache(Integer playerId, CachedDailyUserMatchTeam cached) {
        if (cached.captainId() != null && playerId.equals(cached.captainId())) return "CAPTAIN";
        if (cached.viceCaptainId() != null && playerId.equals(cached.viceCaptainId())) return "VICE_CAPTAIN";
        return null;
    }

    /**
     * "Live" for the my-team overview means the match is currently in flight
     * — including rain-break holds, since the team is already locked and
     * points are still in flux. {@link MatchState#COMPLETE} matches drop out
     * here and are surfaced via the history endpoint instead.
     */
    private static final EnumSet<MatchState> LIVE_STATES =
            EnumSet.of(MatchState.IN_PROGRESS, MatchState.DELAY);

    @Override
    public DailyMyTeamOverview getMyTeamOverview(User user) {
        DailyTeamResponse live = findLiveTeamForUser(user);
        DailyDraftResponse upcoming = tryGetActiveDraft(user);
        return new DailyMyTeamOverview(live, upcoming);
    }

    /**
     * Look up the caller's locked team for any match currently
     * {@code IN_PROGRESS}/{@code DELAY}. Returns {@code null} when the user
     * has no live team — the common case for users who haven't joined the
     * day's match. In IPL doubleheaders we may have two simultaneously live
     * matches; we surface the most recently-started one (newest match.date,
     * match.time) — same ordering the dashboard uses for "Now Playing".
     *
     * <p>Two-query pattern to avoid the {@code HHH90003004} pagination +
     * collection-fetch warning: page the team ids first, then a single
     * fetch with {@code JOIN FETCH playing11} for the chosen id.
     */
    private DailyTeamResponse findLiveTeamForUser(User user) {
        List<Long> ids = teamRepo.findLiveTeamIdsForUser(
                user, LIVE_STATES, PageRequest.of(0, 1));
        if (ids.isEmpty()) return null;
        List<DailyUserMatchTeam> hydrated = teamRepo.findAllByIdInWithPlaying11(ids);
        if (hydrated.isEmpty()) return null;
        DailyUserMatchTeam team = hydrated.get(0);
        Match match = team.getMatch();
        if (match == null) return null;

        // Match is by definition live here (IN_PROGRESS/DELAY); the cache is
        // the authoritative source for match_points (DB rows lag by up to a
        // flush interval). Captain/vc/playing11 are immutable post-lock so
        // they stay sourced from the JPA load.
        Double cachedPoints = null;
        if (liveCache.isWarmedUp(match.getId())) {
            DailyLiveMatchTeamCache.CachedTeamWithRank cached =
                    liveCache.getByUserWithRank(match.getId(), user.getId());
            if (cached != null) {
                metrics.onCacheHit();
                cachedPoints = cached.team().matchPoints();
            } else {
                metrics.onCacheMiss();
            }
        } else {
            metrics.onCacheMiss();
        }
        Double matchPoints = cachedPoints != null ? cachedPoints : team.getMatchPoints();

        Map<Integer, Double> ppMap = new HashMap<>();
        for (PlayerPoints pp : playerPointsRepository.findByMatchId(match.getId())) {
            ppMap.put(pp.getPlayerId(), pp.getPlayerpoints());
        }

        List<PlayerDetailResponse> playing11 = new ArrayList<>();
        if (team.getPlaying11() != null && !team.getPlaying11().isEmpty()) {
            List<PlayerData> players = cricketDao.findPlayersByIds(new ArrayList<>(team.getPlaying11()));
            Map<Integer, PlayerData> byId = new HashMap<>();
            for (PlayerData pd : players) byId.put(pd.id(), pd);
            Map<Integer, String> playerTeamMap = buildPlayerTeamMap(match);

            for (Integer pid : team.getPlaying11()) {
                PlayerData pd = byId.get(pid);
                String name = pd != null ? pd.name() : null;
                String role = pd != null && pd.role() != null ? pd.role().name() : null;
                String tag = resolveTag(pid, team);
                playing11.add(new PlayerDetailResponse(pid, name, role,
                        ppMap.getOrDefault(pid, 0.0), tag, playerTeamMap.get(pid)));
            }
        }

        return new DailyTeamResponse(true, null,
                user.getId(), user.getUsername(), user.getFirstname(),
                match.getId(), matchPoints,
                team.getCaptainId(), team.getViceCaptainId(),
                playing11);
    }

    /**
     * Soft variant of {@link #getActiveDraft(User)} for the overview path:
     * returns {@code null} when there's no upcoming match to play, instead
     * of throwing. The build-flow endpoints still throw (they wouldn't have
     * anything coherent to do with a null match) — only the my-team overview
     * needs to gracefully render an "only live, no upcoming" or "no daily
     * activity" empty state.
     */
    private DailyDraftResponse tryGetActiveDraft(User user) {
        Match next = matchService.findNextUpcomingMatch();
        if (next == null || next.getDate() == null || next.getTime() == null
                || !toIST(next.getDate(), next.getTime(), next.getTimezone()).isAfter(nowDateTime())) {
            return null;
        }
        // Reuse the canonical builder so the wire shape is identical to
        // /api/daily/me/draft — easier on the FE than two near-identical types.
        return getActiveDraft(user);
    }

    @Override
    public List<DailyMatchHistoryResponse> getHistory(User user) {
        List<DailyUserMatchTeam> teams = teamRepo.findHistoryByUser(user);
        List<DailyMatchHistoryResponse> result = new ArrayList<>(teams.size());
        for (DailyUserMatchTeam t : teams) {
            Match m = t.getMatch();
            String teamA = m != null && m.getTeamA() != null ? m.getTeamA().getShortName() : null;
            String teamB = m != null && m.getTeamB() != null ? m.getTeamB().getShortName() : null;
            String matchDesc = m != null ? m.getMatchDesc() : null;
            List<Integer> ids = t.getPlaying11() != null ? new ArrayList<>(t.getPlaying11()) : List.of();

            // Compute the user's competition rank for this match using the
            // same primitive DailyLeaderboardService.computeCurrentUserEntry
            // uses (countAbove + 1), so a row's rank here is identical to
            // what the leaderboard hub shows for the same match. This issues
            // one COUNT per history row — fine for typical per-user history
            // sizes (tens of matches) and matches the leaderboard's own
            // ranking semantics for tied scores (competition rank: 1, 1, 3,
            // not dense rank). Skipped when matchId or points are missing
            // (data hygiene; defensive).
            Integer rank = null;
            if (m != null && m.getId() != null) {
                long above = teamRepo.countAboveByMatchId(m.getId(), t.getMatchPoints());
                rank = (int) Math.min(Integer.MAX_VALUE, above + 1);
            }

            result.add(new DailyMatchHistoryResponse(
                    m != null ? m.getId() : null,
                    matchDesc,
                    m != null ? m.getDate() : null,
                    teamA, teamB,
                    t.getMatchPoints(),
                    rank,
                    t.getCaptainId(), t.getViceCaptainId(),
                    ids));
        }
        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Match loadMatch(Integer matchId) {
        if (matchId == null) {
            throw new ResourceNotFoundException("matchId is required");
        }
        return cricketDao.findMatchById(matchId)
                .map(cricketEntities::toMatch)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
    }

    /**
     * Resolve the single match all build-flow endpoints act on.
     *
     * <p>Daily Challenge is, by design, scoped to the <b>single next upcoming
     * match</b> — the same match returned by
     * {@link MatchService#findNextUpcomingMatch()}. None of the build-flow
     * endpoints ({@code GET /api/daily/me/players}, {@code GET /api/daily/me/draft},
     * {@code POST /api/daily/team-update}) take a {@code matchId} from the
     * client; the server is the only authority for which match is in play.
     * This mirrors the season-long {@code /api/seasons/me/*} +
     * {@code /api/seasons/transfer-update} convention and eliminates the
     * "pre-build a team for a future match by editing the URL" attack class.
     *
     * <p>Per-match read endpoints ({@code POST /api/daily/team},
     * {@code GET /api/daily/match/{id}/leaderboard}) accept arbitrary past
     * matchIds because they're addressing legitimately historical data; they
     * do not use this guard.
     */
    private Match requireActiveDailyMatch() {
        Match next = matchService.findNextUpcomingMatch();
        if (next == null) {
            throw new CommonException("No upcoming match is currently available for Daily Challenge");
        }
        // findNextUpcomingMatch already filters strictly to future matches, but
        // re-check defensively so a request that races match start gets a
        // clean 400 instead of silently allowing a write.
        if (next.getDate() == null || next.getTime() == null
                || !toIST(next.getDate(), next.getTime(), next.getTimezone()).isAfter(nowDateTime())) {
            throw new CommonException(
                    "Team editing is locked. Match has started — daily teams are finalized.");
        }
        return next;
    }


    private Set<Integer> collectMatchPool(Integer teamAId, Integer teamBId) {
        Set<Integer> ids = new HashSet<>();
        for (PlayerWithTeamData p : cricketDao.findPlayersWithTeamByTeamIds(List.of(teamAId, teamBId))) {
            ids.add(p.id());
        }
        return ids;
    }

    private List<Player> loadPlayers(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<PlayerData> playerData = cricketDao.findPlayersByIds(ids);
        Map<Integer, PlayerData> byId = new HashMap<>();
        for (PlayerData pd : playerData) byId.put(pd.id(), pd);

        List<Player> players = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            PlayerData pd = byId.get(id);
            if (pd != null) players.add(cricketEntities.toPlayer(pd));
        }
        return players;
    }

    private Map<Integer, FantasyPlayerConfig> buildConfigMap(Integer leagueId) {
        if (leagueId == null) return Map.of();
        List<FantasyPlayerConfig> configs = fantasyPlayerConfigRepository.findByLeagueId(leagueId);
        Map<Integer, FantasyPlayerConfig> map = new HashMap<>(configs.size());
        for (FantasyPlayerConfig cfg : configs) {
            map.put(cfg.getPlayerId(), cfg);
        }
        return map;
    }

    private List<PlayerBrief> resolvePlayerBriefs(List<Integer> ids,
                                                  Map<Integer, String> playerTeamMap) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<PlayerData> rows = cricketDao.findPlayersByIds(new ArrayList<>(ids));
        Map<Integer, PlayerData> byId = new HashMap<>();
        for (PlayerData pd : rows) byId.put(pd.id(), pd);

        List<PlayerBrief> briefs = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            PlayerData pd = byId.get(id);
            if (pd != null) {
                briefs.add(new PlayerBrief(pd.id(), pd.name(), pd.role(),
                        playerTeamMap.get(id)));
            }
        }
        return briefs;
    }

    /**
     * Build {@code playerId → team-shortName} for the two sides playing
     * {@code match}. Daily Challenge has the convenient property that the
     * pool is exactly two teams, so a single
     * {@link CricketMasterDataDao#findPlayersWithTeamByTeamIds(List)} call
     * gets us the franchise label for every player a draft/locked-team
     * payload could possibly reference. Returns an empty map (never null)
     * when the match has no teams assigned, so callers can use
     * {@code map.get(pid)} directly without a null-guard.
     */
    private Map<Integer, String> buildPlayerTeamMap(Match match) {
        if (match == null) return Map.of();
        Integer teamAId = match.getTeamA() != null ? match.getTeamA().getId() : null;
        Integer teamBId = match.getTeamB() != null ? match.getTeamB().getId() : null;
        if (teamAId == null || teamBId == null) return Map.of();

        List<PlayerWithTeamData> rows =
                cricketDao.findPlayersWithTeamByTeamIds(List.of(teamAId, teamBId));
        Map<Integer, String> map = new HashMap<>(rows.size());
        for (PlayerWithTeamData p : rows) {
            map.put(p.id(), p.teamShortName());
        }
        return map;
    }

    /**
     * Same lock-window semantics as season-long: a match becomes locked from
     * its scheduled start time until the configured number of post-start
     * minutes have elapsed. After that, the lock is permanent (match in flight).
     */
    private boolean isMatchLocked(Match match, LocalDateTime now) {
        if (match == null || match.getDate() == null || match.getTime() == null) return false;
        LocalDateTime start = toIST(match.getDate(), match.getTime(), match.getTimezone());
        return !now.isBefore(start);
    }

    private TeamBrief toBrief(com.cricket.fantasyleague.entity.table.Team t) {
        if (t == null) return null;
        return new TeamBrief(t.getId(), t.getName(), t.getShortName());
    }

    private String resolveTag(Integer playerId, DailyUserMatchTeam team) {
        if (team.getCaptainId() != null && playerId.equals(team.getCaptainId())) return "CAPTAIN";
        if (team.getViceCaptainId() != null && playerId.equals(team.getViceCaptainId())) return "VICE_CAPTAIN";
        return null;
    }
}
