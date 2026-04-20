package com.cricket.fantasyleague.payload.response;

public record UserProfileResponse(
        Long id,
        String username,
        String firstname,
        String lastname,
        String email,
        String favteam,
        Double totalPoints,
        Integer boosterLeft,
        Integer transferLeft
) {
}
