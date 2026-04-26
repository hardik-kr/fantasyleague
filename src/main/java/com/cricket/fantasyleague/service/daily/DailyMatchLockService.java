package com.cricket.fantasyleague.service.daily;

/**
 * Promotes Daily Challenge drafts into locked teams when a match starts.
 *
 * Wired into {@code LiveMatchWorkflowService.lockTeamsForMatch} via a single
 * additive call. Idempotent: safe to invoke repeatedly for the same match
 * (already-locked users are skipped via the {@code (user_id, match_id)}
 * unique constraint on {@code daily_user_match_team}).
 */
public interface DailyMatchLockService {

    /**
     * Move every Daily draft for {@code matchId} into the locked-team table and
     * delete the source drafts.  No-op when no daily drafts exist for the match
     * (so season-only matches incur a single zero-row scan).
     */
    void lockTeamsForMatch(Integer matchId);
}
