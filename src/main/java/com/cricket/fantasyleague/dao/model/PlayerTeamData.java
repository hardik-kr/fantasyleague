package com.cricket.fantasyleague.dao.model;

public record PlayerTeamData(
        Integer playerId,
        Integer teamId,
        Boolean isActive
) {
}
