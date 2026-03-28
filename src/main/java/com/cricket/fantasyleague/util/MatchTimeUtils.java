package com.cricket.fantasyleague.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * All match date/time in DB is stored in IST (Asia/Kolkata).
 * This utility ensures "now" is always evaluated in the same timezone,
 * regardless of the server's JVM default timezone.
 */
public final class MatchTimeUtils {

    private static final ZoneId MATCH_ZONE = ZoneId.of("Asia/Kolkata");

    private MatchTimeUtils() {}

    public static LocalDate nowDate() {
        return ZonedDateTime.now(MATCH_ZONE).toLocalDate();
    }

    public static LocalTime nowTime() {
        return ZonedDateTime.now(MATCH_ZONE).toLocalTime();
    }

    public static LocalDateTime nowDateTime() {
        return ZonedDateTime.now(MATCH_ZONE).toLocalDateTime();
    }
}
