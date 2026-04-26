package com.cricket.fantasyleague.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-player line item for locked-team views (with live points).
 *
 * <p>{@code team} is the player's franchise short name (e.g. "GT", "CSK")
 * — populated by the Daily Challenge surface to render a per-player team
 * column on the my-team page. Season-long callers leave it {@code null};
 * {@code @JsonInclude(NON_NULL)} keeps the field out of those payloads.
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
