package com.cricket.fantasyleague.dao.model;

/**
 * Read-only DTO for player data from the cricketapi database.
 */
public record PlayerData(
        Integer id,
        String name,
        Integer teamId,
        String teamName,
        String role
) {
}
