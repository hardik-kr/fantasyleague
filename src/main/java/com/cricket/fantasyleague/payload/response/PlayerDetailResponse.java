package com.cricket.fantasyleague.payload.response;

public record PlayerDetailResponse(
        Integer playerId,
        String name,
        String role,
        Double points,
        String tag
) {
}
