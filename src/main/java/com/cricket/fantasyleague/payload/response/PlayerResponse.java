package com.cricket.fantasyleague.payload.response;

import com.cricket.fantasyleague.entity.enums.PlayerType;

public record PlayerResponse(
        Integer id,
        String name,
        PlayerType role,
        Integer teamId,
        String teamName,
        String teamShortName,
        Double credit,
        Boolean overseas,
        Boolean uncapped,
        Double totalPoints,
        Boolean isActive
) {
}
