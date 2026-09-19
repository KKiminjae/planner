package com.planner.photo_calendar.record.dto.response;

import com.planner.photo_calendar.record.DailyRecord;

import java.time.LocalDate;
import java.time.LocalTime;

public record MonthlyRecordResponse(
        Long id,
        LocalDate recordDate,
        LocalTime recordTime,
        String imageUrl
) {
    public static MonthlyRecordResponse from(DailyRecord dailyRecord){
        return new MonthlyRecordResponse(
                dailyRecord.getId(),
                dailyRecord.getRecordDate(),
                dailyRecord.getRecordTime(),
                dailyRecord.getImageUrl()
        );
    }
}
