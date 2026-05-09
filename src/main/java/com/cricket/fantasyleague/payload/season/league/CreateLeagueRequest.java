package com.cricket.fantasyleague.payload.season.league;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/seasons/leagues} — payload to create a new
 * private league. The creator becomes the first member automatically.
 */
public record CreateLeagueRequest(

        @NotBlank(message = "name is required")
        @Size(min = 3, max = 80, message = "name must be 3..80 characters")
        String name,

        @NotNull(message = "maxMembers is required")
        @Min(value = 2, message = "maxMembers must be at least 2")
        @Max(value = 1000, message = "maxMembers must be at most 1000")
        Integer maxMembers
) {
}
