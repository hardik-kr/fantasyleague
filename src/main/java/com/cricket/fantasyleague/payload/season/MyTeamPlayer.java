package com.cricket.fantasyleague.payload.season;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyTeamPlayer(
        Integer id,
        String name,
        PlayerType role,
        String team,
        Double points
) {
}
