package com.cricket.fantasyleague.entity.enums;

public enum MatchState {
    PREVIEW,
    UPCOMING,
    IN_PROGRESS,
    COMPLETE;

    /**
     * Converts an API string like "Upcoming", "In Progress", "Complete"
     * to the corresponding enum constant. Returns null for unknown values.
     */
    public static MatchState fromApiValue(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase().replace(" ", "_");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
