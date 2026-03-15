package com.cricket.fantasyleague.dao.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchData(
        Integer id,
        LocalDate date,
        LocalTime time,
        String venue,
        Integer matchnum,
        String result,
        String toss,
        Integer teamAId,
        Integer teamBId
) {
}
