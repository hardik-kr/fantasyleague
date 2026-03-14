package com.cricket.fantasyleague.service.cricketapi.model;

public record CricketApiPlayerRow(
        Integer id,
        String name,
        Integer teamId,
        String role
) {
}
