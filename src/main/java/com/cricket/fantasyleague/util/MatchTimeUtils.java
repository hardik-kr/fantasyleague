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

    /**
     * Converts a DB-stored date+time to IST if the DB timezone differs from IST.
     * If dbTimezone is null or already IST, returns the original datetime as-is.
     */
    public static LocalDateTime toIST(LocalDate date, LocalTime time, String dbTimezone) {
        LocalDateTime dt = LocalDateTime.of(date, time);
        if (dbTimezone == null || dbTimezone.equals("Asia/Kolkata") || dbTimezone.equals("IST")) {
            return dt;
        }
        ZoneId sourceZone = ZoneId.of(dbTimezone);
        return dt.atZone(sourceZone).withZoneSameInstant(MATCH_ZONE).toLocalDateTime();
    }
}
