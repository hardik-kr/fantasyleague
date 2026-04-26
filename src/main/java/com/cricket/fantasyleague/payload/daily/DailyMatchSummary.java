package com.cricket.fantasyleague.payload.daily;

import java.time.LocalDate;
import java.time.LocalTime;

import com.cricket.fantasyleague.payload.response.TeamBrief;

public record DailyMatchSummary(
        Integer matchId,
        LocalDate date,
        LocalTime time,
        String matchDesc,
        String venue,
        TeamBrief teamA,
        TeamBrief teamB,
        boolean hasDraft,
        boolean hasLockedTeam,
        boolean locked
) {
}
