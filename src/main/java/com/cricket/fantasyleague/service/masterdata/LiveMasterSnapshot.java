package com.cricket.fantasyleague.service.masterdata;

import java.time.Instant;

/**
 * In-memory scope for periodic master-cache refresh during a live season-long match:
 * one match row plus the set of player ids whose {@code PlayerResponse} rows should be re-read from DB.
 */
public record LiveMasterSnapshot(int matchId, int leagueId, int[] playerIds, Instant updatedAt) {
}
