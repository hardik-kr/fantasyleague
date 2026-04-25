package com.cricket.fantasyleague.payload.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record PlayerMatchPointsResponse(
        Integer matchId,
        String matchDesc,
        LocalDate date,
        LocalTime time,
        String teamAShortName,
        String teamBShortName,
        Boolean isMatchComplete,
        Double points
) {
}
