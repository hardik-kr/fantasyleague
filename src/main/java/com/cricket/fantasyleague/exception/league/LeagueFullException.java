package com.cricket.fantasyleague.exception.league;

/**
 * Thrown when a user attempts to join a private league whose membership
 * has already reached {@code maxMembers}. Mapped to HTTP 400 by
 * {@link com.cricket.fantasyleague.exception.GlobalExceptionHandler}.
 */
public class LeagueFullException extends RuntimeException {

    public LeagueFullException(String message) {
        super(message);
    }
}
