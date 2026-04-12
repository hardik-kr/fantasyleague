package com.cricket.fantasyleague.payload.response;

public record MatchPlayerPointsResponse(
        Integer playerId,
        String playerName,
        String role,
        Double points
) {
}
