package com.cricket.fantasyleague.payload.season;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.payload.response.PlayerBrief;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DraftResponse(
        String message,
        Integer matchId,
        LocalDate matchDate,
        LocalTime matchTime,
        String matchDesc,
        String teamA,
        String teamB,
        Boolean hasDraft,
        Booster booster,
        Integer transfersUsed,
        Integer captainId,
        Integer viceCaptainId,
        Integer tripleScorerId,
        List<PlayerBrief> playing11,
        Integer transferLeft,
        Integer boosterLeft,
        Map<String, Integer> boosterLeftDetail,
        Boolean isFreeTransferWindow,
        List<String> usedBoosters,
        List<Integer> previousPlaying11
) {
}
