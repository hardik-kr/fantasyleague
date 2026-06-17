package com.cricket.fantasyleague.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-player line item for locked-team views (with live points).
 *
 * <p>{@code team} is the player's franchise short name (e.g. "GT", "CSK", "NZW").
 * Populated from the master player cache ({@code MasterDataReadService}) for both
 * Daily Challenge and season-long locked-team views.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerDetailResponse(
        Integer playerId,
        String name,
        String role,
        Double points,
        String tag,
        String team
) {
}
