package com.cricket.fantasyleague.payload.daily;

import java.util.List;

import com.cricket.fantasyleague.payload.response.PlayerDetailResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-(user, match) team view for Daily Challenge.
 *
 * <p>Mirrors the shape of season-long's {@code UserTeamResponse}: returns the
 * locked team for the requested {@code (userId, matchId)} pair, or
 * {@code found=false} if none exists. The pre-match draft preview is
 * served by a separate {@code GET /api/daily/me/draft} call (same
 * separation season-long has between {@code /me/draft} and {@code /team}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyTeamResponse(
        boolean found,
        String message,
        Long userId,
        String username,
        String firstname,
        Integer matchId,
        Double matchPoints,
        Integer captainId,
        Integer viceCaptainId,
        List<PlayerDetailResponse> playing11
) {
}
