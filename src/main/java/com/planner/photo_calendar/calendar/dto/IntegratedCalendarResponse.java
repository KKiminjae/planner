package com.planner.photo_calendar.calendar.dto;

import java.time.LocalDate;

public record IntegratedCalendarResponse(
        LocalDate date,

        int completedCount,

        int totalCount
) {
}
