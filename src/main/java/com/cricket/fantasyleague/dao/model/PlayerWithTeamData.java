package com.cricket.fantasyleague.dao.model;

import com.cricket.fantasyleague.entity.enums.PlayerType;

public record PlayerWithTeamData(
        Integer id,
        String name,
        PlayerType role,
        Integer teamId,
        String teamName,
        String teamShortName
) {
}
