package com.cricket.fantasyleague.exception;

/**
 * Thrown when a request lands on an endpoint whose owning feature is gated off
 * by configuration (e.g. {@code fantasy.daily-challenge.enabled=false}). This
 * is <b>not</b> a server error — the route exists and the server is healthy;
 * the feature is just intentionally turned off, possibly temporarily.
 *
 * <p>Mapped to HTTP 503 Service Unavailable by
 * {@link GlobalExceptionHandler}: clients get a clear "come back later"
 * signal instead of a misleading 500, and don't trip error-rate alerts.
 */
public class FeatureDisabledException extends RuntimeException {

    public FeatureDisabledException(String message) {
        super(message);
    }
}
