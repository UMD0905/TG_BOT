package com.umd.stobooking.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeUtil {

    public static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");

    private DateTimeUtil() {}

    public static LocalDateTime nowTashkent() {
        return ZonedDateTime.now(TASHKENT).toLocalDateTime();
    }

    public static LocalDate todayTashkent() {
        return LocalDate.now(TASHKENT);
    }
}
