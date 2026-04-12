package com.cricket.fantasyleague.payload.response;

import java.time.LocalDate;
import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster;

public record MatchHistoryResponse(
        Integer matchId,
        LocalDate date,
        String teamA,
        String teamB,
        Double matchPoints,
        Booster boosterUsed,
        Integer transfersUsed,
        Integer captainId,
        Integer viceCaptainId,
        List<Integer> playing11
) {
}
