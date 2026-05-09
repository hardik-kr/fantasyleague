package com.cricket.fantasyleague.service.daily;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.cache.DailyLiveMatchTeamCache;
import com.cricket.fantasyleague.cache.dto.CachedDailyUserMatchTeam;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;
import com.cricket.fantasyleague.payload.daily.DailyLeaderboardEntry;
import com.cricket.fantasyleague.payload.daily.DailyLeaderboardPageResponse;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamRepository;

/**
 * Per-match Daily Challenge leaderboard. Each match is scored in isolation —
 * there is no overall/season aggregation in daily mode.
 *
 * <p>Hot path (match {@code IN_PROGRESS}): served from
 * {@link DailyLiveMatchTeamCache} — zero DB round-trips. The cache is updated
 * every live tick by {@link DailyMatchPointsService}, so leaderboard reads
 * return fresh ranked data without contending with the per-tick recompute.
 *
 * <p>Cold path (match completed and cache evicted, or never warmed): falls
 * back to the existing {@code daily_user_match_team.match_points} sort, which
 * is backed by the {@code idx_daily_match_points (match_id, match_points DESC)}
 * index. API shape is identical regardless of which path served the request.
 */
@Service
public class DailyLeaderboardServiceImpl implements DailyLeaderboardService {

    private final DailyUserMatchTeamRepository teamRepo;
    private final DailyLiveMatchTeamCache cache;
    private final DailyMetrics metrics;

    public DailyLeaderboardServiceImpl(DailyUserMatchTeamRepository teamRepo,
                                       DailyLiveMatchTeamCache cache,
                                       DailyMetrics metrics) {
        this.teamRepo = teamRepo;
        this.cache = cache;
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public DailyLeaderboardPageResponse getMatchLeaderboard(Integer matchId, int page, int size, User currentUser) {
        if (cache.isWarmedUp(matchId)) {
            metrics.onCacheHit();
            return buildFromCache(matchId, page, size, currentUser);
        }
        metrics.onCacheMiss();
        return buildFromDb(matchId, page, size, currentUser);
    }

    private DailyLeaderboardPageResponse buildFromCache(Integer matchId, int page, int size, User currentUser) {
        DailyLiveMatchTeamCache.RankedSnapshot snapshot = cache.getRankedPage(matchId, page, size);
        List<DailyLeaderboardEntry> rows = new ArrayList<>(snapshot.rows().size());
        int rank = page * size + 1;
        for (CachedDailyUserMatchTeam t : snapshot.rows()) {
            rows.add(new DailyLeaderboardEntry(
                    rank++,
                    t.userId(),
                    t.username(),
                    t.firstname(),
                    t.matchPoints(),
                    t.captainId(),
                    t.viceCaptainId()));
        }

        DailyLeaderboardEntry meEntry = null;
        if (currentUser != null && currentUser.getId() != null) {
            DailyLiveMatchTeamCache.CachedTeamWithRank mine = cache.getByUserWithRank(matchId, currentUser.getId());
            if (mine != null) {
                CachedDailyUserMatchTeam t = mine.team();
                meEntry = new DailyLeaderboardEntry(
                        mine.rank(),
                        currentUser.getId(),
                        currentUser.getUsername(),
                        currentUser.getFirstname(),
                        t.matchPoints(),
                        t.captainId(),
                        t.viceCaptainId());
            }
        }

        return new DailyLeaderboardPageResponse(matchId, rows, page, size,
                snapshot.totalElements(), snapshot.totalPages(), meEntry);
    }

    private DailyLeaderboardPageResponse buildFromDb(Integer matchId, int page, int size, User currentUser) {
        Page<DailyUserMatchTeam> ranked = teamRepo.findRankedByMatchId(matchId, PageRequest.of(page, size));

        List<DailyLeaderboardEntry> rows = new ArrayList<>(ranked.getNumberOfElements());
        int rank = page * size + 1;
        for (DailyUserMatchTeam t : ranked.getContent()) {
            User u = t.getUser();
            rows.add(new DailyLeaderboardEntry(
                    rank++,
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : null,
                    u != null ? u.getFirstname() : null,
                    t.getMatchPoints(),
                    t.getCaptainId(),
                    t.getViceCaptainId()));
        }

        DailyLeaderboardEntry meEntry = null;
        if (currentUser != null) {
            meEntry = computeCurrentUserEntryFromDb(matchId, currentUser);
        }

        return new DailyLeaderboardPageResponse(matchId, rows, page, size,
                ranked.getTotalElements(), ranked.getTotalPages(), meEntry);
    }

    private DailyLeaderboardEntry computeCurrentUserEntryFromDb(Integer matchId, User user) {
        var maybeTeam = teamRepo.findByMatchAndUser(loadMatch(matchId), user);
        if (maybeTeam.isEmpty()) return null;
        DailyUserMatchTeam t = maybeTeam.get();
        long above = teamRepo.countAboveByMatchId(matchId, t.getMatchPoints());
        int rank = (int) Math.min(Integer.MAX_VALUE, above + 1);
        return new DailyLeaderboardEntry(
                rank,
                user.getId(),
                user.getUsername(),
                user.getFirstname(),
                t.getMatchPoints(),
                t.getCaptainId(),
                t.getViceCaptainId());
    }

    /** Lightweight match handle for the unique-key lookup; only id is needed by JPA. */
    private Match loadMatch(Integer matchId) {
        Match m = new Match();
        m.setId(matchId);
        return m;
    }
}
