package com.cricket.fantasyleague.dao.model;

/**
 * Read-only DTO for team data from the cricketapi database.
 * Column mapping: cricketapi.teams.country → Team.name, cricketapi.teams.name → Team.inital
 */
public record TeamData(
        Integer id,
        String country,
        String shortName
) {
}
