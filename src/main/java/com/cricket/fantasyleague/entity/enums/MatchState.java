package com.cricket.fantasyleague.entity.enums;

public enum MatchState {
    PREVIEW,
    UPCOMING,
    IN_PROGRESS,
    COMPLETE;

    /**
     * Converts an API string to the corresponding enum constant.
     * Handles variations like "Live", "Innings Break", "Stumps", "Toss", etc.
     */
    public static MatchState fromApiValue(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase().replace(" ", "_");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Map known live-game states to IN_PROGRESS
        }
        switch (normalized) {
            case "LIVE":
            case "INNINGS_BREAK":
            case "STUMPS":
            case "TOSS":
            case "LUNCH":
            case "TEA":
            case "DRINKS":
            case "STRATEGIC_TIMEOUT":
                return IN_PROGRESS;
            default:
                return null;
        }
    }
}
