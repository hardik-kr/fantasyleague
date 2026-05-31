package com.cricket.fantasyleague.payload.season;

import java.time.LocalDate;
import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lean payload for {@code GET /api/seasons/me/my-team} — only fields the
 * read-only pitch view needs. Draft/transfer metadata stays on
 * {@link DraftResponse} ({@code /me/draft}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyTeamResponse(
        String message,
        String matchState,
        String matchDesc,
        LocalDate matchDate,
        String teamA,
        String teamB,
        Booster booster,
        Integer captainId,
        Integer viceCaptainId,
        Integer tripleScorerId,
        Double matchPoints,
        List<MyTeamPlayer> playing11
) {
}
