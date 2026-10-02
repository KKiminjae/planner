package com.planner.photo_calendar.common.time;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class ApplicationTime {
    public static final ZoneId CALENDAR_ZONE = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock.withZone(CALENDAR_ZONE));
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    public static LocalDate calendarDate(LocalDateTime storedUtc) {
        return storedUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(CALENDAR_ZONE).toLocalDate();
    }

    public static LocalDateTime startOfDayUtc(LocalDate calendarDate) {
        return calendarDate.atStartOfDay(CALENDAR_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
