package com.cricket.fantasyleague.payload.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchResponse(
        Integer id,
        LocalDate date,
        LocalTime time,
        String venue,
        String toss,
        String result,
        Boolean isMatchComplete,
        String matchState,
        String matchDesc,
        TeamBrief teamA,
        TeamBrief teamB
) {
}
