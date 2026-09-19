package com.planner.photo_calendar.category.dto.response;

import com.planner.photo_calendar.category.dto.AnnualRecordStatus;

public record AnnualRecordResponse(
        int month,
        int day,
        AnnualRecordStatus status
) {
}
