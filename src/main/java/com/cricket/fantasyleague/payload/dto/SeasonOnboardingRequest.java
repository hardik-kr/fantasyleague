package com.cricket.fantasyleague.payload.dto;

import jakarta.validation.constraints.NotBlank;

public record SeasonOnboardingRequest(
        @NotBlank(message = "Favorite team is required")
        String favteam) {
}
