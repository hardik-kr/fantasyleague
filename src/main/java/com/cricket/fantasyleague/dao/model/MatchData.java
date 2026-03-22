package com.cricket.fantasyleague.dao.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchData(
        Integer id,
        LocalDate date,
        Boolean isMatchComplete,
        String matchtype,
        String result,
        LocalTime time,
        String timezone,
        String venue,
        String toss,
        Integer leagueId,
        Integer momPlayerId,
        Integer teamAId,
        Integer teamBId
) {
}
