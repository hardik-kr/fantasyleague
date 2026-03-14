package com.cricket.fantasyleague.service.cricketapi.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record CricketApiMatchRow(
        Integer id,
        LocalDate date,
        LocalTime time,
        String venue,
        String result,
        String toss,
        Integer teamAId,
        Integer teamBId
) {
}
