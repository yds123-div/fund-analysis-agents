package com.hex.fund.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Date utility with China market trading calendar support.
 */
public final class DateUtil {

    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private DateUtil() {
    }

    /**
     * Basic trading day check (weekday only, holidays not yet handled).
     */
    public static boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        // TODO: integrate holiday calendar from data source
    }

    /**
     * Check if current time is within A-share trading hours.
     */
    public static boolean isInTradingHours(LocalDateTime dateTime) {
        if (!isTradingDay(dateTime.toLocalDate())) return false;
        LocalTime time = dateTime.toLocalTime();
        boolean morning = !time.isBefore(MORNING_OPEN) && !time.isAfter(MORNING_CLOSE);
        boolean afternoon = !time.isBefore(AFTERNOON_OPEN) && !time.isAfter(AFTERNOON_CLOSE);
        return morning || afternoon;
    }

    /**
     * Get next trading day from given date.
     */
    public static LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isTradingDay(next)) next = next.plusDays(1);
        return next;
    }

    /**
     * Get previous trading day from given date.
     */
    public static LocalDate previousTradingDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isTradingDay(prev)) prev = prev.minusDays(1);
        return prev;
    }
}
