package com.cricket.fantasyleague.payload.season.league;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lightweight, member-agnostic snapshot of a private league. Returned by
 * {@code GET /api/seasons/leagues/{code}/preview} to power the share-link
 * confirmation dialog — the caller may or may not already be a member.
 *
 * <p>Intentionally omits the member list and creator id (those live on
 * {@link LeagueDetailResponse}, which still requires membership) so this
 * endpoint is safe to expose to anyone holding a valid code: holding a
 * code is the implicit "invite" already.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaguePreviewResponse(
        String code,
        String name,
        Integer memberCount,
        Integer maxMembers,
        boolean isMember,
        boolean full
) {
}
