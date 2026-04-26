package com.cricket.fantasyleague.service.daily;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeam;
import com.cricket.fantasyleague.entity.table.daily.DailyUserMatchTeamDraft;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamDraftRepository;
import com.cricket.fantasyleague.repository.daily.DailyUserMatchTeamRepository;

/**
 * Promote Daily drafts → locked teams. Mirrors the season-long
 * {@code lockMatchTeam} batching strategy (chunked at the same configurable
 * batch size) but is significantly simpler because daily mode has no
 * boosters and no transfer counters to maintain.
 *
 * <p>Idempotency is enforced through:
 * <ul>
 *   <li>Per-batch query of already-locked user ids (skipped on resume).</li>
 *   <li>The {@code uq_daily_user_match} unique constraint as a final safety net.</li>
 *   <li>Source drafts deleted only after the batch commit succeeds.</li>
 * </ul>
 */
@Service
public class DailyMatchLockServiceImpl implements DailyMatchLockService {

    private static final Logger logger = LoggerFactory.getLogger(DailyMatchLockServiceImpl.class);

    private final DailyUserMatchTeamRepository teamRepo;
    private final DailyUserMatchTeamDraftRepository draftRepo;
    private final CricketMasterDataDao cricketDao;
    private final CricketEntityMapper cricketEntities;
    private final DailyMetrics metrics;
    private final int lockBatchSize;
    private final long lockBatchDelayMs;
    private final boolean enabled;

    /**
     * Self-proxy reference. Required so {@link #lockTeamsForMatch(Integer)}
     * can route into {@link #lockBatch(Match, List)} <i>through Spring's
     * transactional proxy</i> — direct {@code this.lockBatch(...)}
     * invocation bypasses the AOP interceptor and {@code @Transactional}
     * silently has no effect (every {@code teamRepo.saveAll} would commit
     * its own per-row transaction). Injected as the impl type, marked
     * {@link Lazy} to break the bean-creation cycle that pure self-injection
     * otherwise causes.
     */
    private final DailyMatchLockServiceImpl self;

    public DailyMatchLockServiceImpl(DailyUserMatchTeamRepository teamRepo,
                                     DailyUserMatchTeamDraftRepository draftRepo,
                                     CricketMasterDataDao cricketDao,
                                     CricketEntityMapper cricketEntities,
                                     DailyMetrics metrics,
                                     @Lazy DailyMatchLockServiceImpl self,
                                     @Value("${fantasy.lock.batch-size:5000}") int lockBatchSize,
                                     @Value("${fantasy.lock.batch-delay-ms:1000}") long lockBatchDelayMs,
                                     @Value("${fantasy.daily-challenge.enabled:false}") boolean enabled) {
        this.teamRepo = teamRepo;
        this.draftRepo = draftRepo;
        this.cricketDao = cricketDao;
        this.cricketEntities = cricketEntities;
        this.metrics = metrics;
        this.self = self;
        this.lockBatchSize = lockBatchSize;
        this.lockBatchDelayMs = lockBatchDelayMs;
        this.enabled = enabled;
    }

    @Override
    public void lockTeamsForMatch(Integer matchId) {
        if (!enabled) return;
        if (matchId == null) return;

        long startNanos = System.nanoTime();
        Match match = cricketDao.findMatchById(matchId).map(cricketEntities::toMatch).orElse(null);
        if (match == null) {
            logger.warn("DailyMatchLock: matchId={} not found, skipping", matchId);
            return;
        }

        long totalDrafts = draftRepo.countByMatch(match);
        if (totalDrafts == 0) {
            logger.debug("DailyMatchLock: no daily drafts for matchId={} — no-op", matchId);
            return;
        }

        int totalPages = (int) Math.ceil((double) totalDrafts / lockBatchSize);
        int totalLocked = 0;
        int totalSkipped = 0;
        logger.info("DailyMatchLock START: matchId={}, drafts={}, batchSize={}, pages={}",
                matchId, totalDrafts, lockBatchSize, totalPages);

        for (int page = 0; page < totalPages; page++) {
            List<Long> ids = draftRepo.findIdsByMatch(match, PageRequest.of(0, lockBatchSize));
            if (ids.isEmpty()) break;

            List<DailyUserMatchTeamDraft> batch = draftRepo.findAllByIdInWithPlaying11(ids);
            // Route through the self-proxy so Spring's @Transactional
            // interceptor wraps the entire batch in a single transaction
            // (a direct this.lockBatch(...) call would bypass the proxy).
            int locked = self.lockBatch(match, batch);
            int skipped = batch.size() - locked;
            totalLocked += locked;
            totalSkipped += skipped;

            // Always delete the source drafts after a successful commit so the
            // next page query slides forward.
            draftRepo.deleteAllInBatch(batch);

            logger.info("DailyMatchLock: matchId={}, page {}/{}, locked={}, totalLocked={}",
                    matchId, page + 1, totalPages, locked, totalLocked);

            if (page < totalPages - 1 && lockBatchDelayMs > 0) {
                try {
                    Thread.sleep(lockBatchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("DailyMatchLock interrupted at page {}/{}", page + 1, totalPages);
                    break;
                }
            }
        }

        metrics.onTeamsLocked(totalLocked);
        metrics.onTeamsLockSkipped(totalSkipped);
        metrics.matchLockTimer().record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        logger.info("DailyMatchLock END: matchId={}, locked={}, skipped={}",
                matchId, totalLocked, totalSkipped);
    }

    /**
     * Promote a draft batch to locked teams. Skips users that already have a
     * row (resume after partial crash) — those drafts are still cleaned up
     * by the outer delete to avoid an infinite loop.
     *
     * <p><b>Visibility contract</b>: {@code public} so Spring's CGLIB-based
     * {@code @Transactional} proxy can intercept the call. <i>Always</i>
     * invoke through the {@link #self} reference, never directly via
     * {@code this} — the latter bypasses the proxy and degrades the method
     * back to "many tiny implicit transactions". External callers should not
     * use this method; it is a batch primitive of {@link #lockTeamsForMatch}.
     */
    @Transactional
    public int lockBatch(Match match, List<DailyUserMatchTeamDraft> batch) {
        if (batch.isEmpty()) return 0;

        List<User> users = batch.stream().map(DailyUserMatchTeamDraft::getUser).toList();
        Set<Long> existingUserIds = collectExistingUserIds(match, users);

        List<DailyUserMatchTeam> toInsert = new ArrayList<>(batch.size());
        for (DailyUserMatchTeamDraft draft : batch) {
            if (draft.getUser() == null) continue;
            if (existingUserIds.contains(draft.getUser().getId())) {
                continue;
            }
            DailyUserMatchTeam team = new DailyUserMatchTeam(
                    draft.getUser(), match,
                    draft.getCaptainId(), draft.getViceCaptainId(),
                    draft.getPlaying11() != null ? new ArrayList<>(draft.getPlaying11()) : new ArrayList<>());
            toInsert.add(team);
        }
        if (!toInsert.isEmpty()) {
            teamRepo.saveAll(toInsert);
        }
        return toInsert.size();
    }

    private Set<Long> collectExistingUserIds(Match match, List<User> users) {
        if (users == null || users.isEmpty()) return Set.of();
        List<Long> userIds = users.stream()
                .filter(u -> u != null && u.getId() != null)
                .map(User::getId)
                .toList();
        if (userIds.isEmpty()) return Set.of();
        return new HashSet<>(teamRepo.findExistingUserIds(match, userIds));
    }
}
