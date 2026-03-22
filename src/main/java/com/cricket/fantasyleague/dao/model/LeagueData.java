package com.cricket.fantasyleague.dao.model;

public record LeagueData(
        Integer id,
        String name,
        String shortName,
        String format,
        String country
) {
}
