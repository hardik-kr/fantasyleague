package com.cricket.fantasyleague.payload.daily;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyLeaderboardPageResponse(
        Integer matchId,
        List<DailyLeaderboardEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        DailyLeaderboardEntry currentUser
) {
}
