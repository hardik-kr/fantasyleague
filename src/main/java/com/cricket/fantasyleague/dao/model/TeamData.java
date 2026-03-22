package com.cricket.fantasyleague.dao.model;

public record TeamData(
        Integer id,
        String name,
        String shortName,
        Integer leagueId
) {
}
