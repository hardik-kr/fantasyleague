package com.cricket.fantasyleague.payload.season;

import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.payload.response.PlayerDetailResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserTeamResponse(
        boolean found,
        String message,
        Long userId,
        String username,
        String firstname,
        Integer matchId,
        Double matchPoints,
        Booster boosterUsed,
        Integer transfersUsed,
        Integer captainId,
        Integer viceCaptainId,
        List<PlayerDetailResponse> playing11
) {
}
