package com.cricket.fantasyleague.payload.response;

public record AppConfigResponse(
        Integer activeLeagueId,
        String name,
        Integer year,
        String status,
        Integer totalTransfer,
        Integer totalBooster) {
}
