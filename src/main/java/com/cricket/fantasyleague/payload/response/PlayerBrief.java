package com.cricket.fantasyleague.payload.response;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal player descriptor used by draft/preview payloads.
 *
 * <p>{@code team} is the player's franchise short name (e.g. "GT", "CSK")
 * — populated by the Daily Challenge surface to render a per-player team
 * column on the my-team page. Season-long callers leave it {@code null}
 * (their UI doesn't need it); {@code @JsonInclude(NON_NULL)} keeps the
 * field out of those payloads entirely so it's a zero-cost addition.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerBrief(Integer id, String name, PlayerType role, String team) {
}
