package com.cricket.fantasyleague.payload.season.league;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned from create / join / get-detail-lite endpoints. Carries all
 * fields the UI needs to render the detail header without a follow-up
 * request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeagueResponse(
        String code,
        String name,
        Integer maxMembers,
        Integer memberCount,
        Long createdById,
        boolean isCreator,
        LocalDateTime createdAt
) {
}
