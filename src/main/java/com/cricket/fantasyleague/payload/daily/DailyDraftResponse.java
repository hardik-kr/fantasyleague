package com.cricket.fantasyleague.payload.daily;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.cricket.fantasyleague.payload.response.PlayerBrief;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyDraftResponse(
        String message,
        Integer matchId,
        LocalDate matchDate,
        LocalTime matchTime,
        String matchDesc,
        String teamA,
        String teamB,
        boolean hasDraft,
        Integer captainId,
        Integer viceCaptainId,
        List<PlayerBrief> playing11,
        boolean locked
) {
}
