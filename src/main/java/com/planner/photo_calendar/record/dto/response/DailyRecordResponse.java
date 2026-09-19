package com.planner.photo_calendar.record.dto.response;

import com.planner.photo_calendar.record.DailyRecord;
import com.planner.photo_calendar.record.dto.request.DailyRecordCreateRequest;

import java.time.LocalDate;
import java.time.LocalTime;

public record DailyRecordResponse (
        Long id,
        Long categoryId,
        String categoryName,
        LocalDate recordDate,
        LocalTime recordTime,
        String memo,
        String imageUrl
) {
    public static DailyRecordResponse from(DailyRecord record){
        return new DailyRecordResponse(
                record.getId(),
                record.getCategory().getId(),
                record.getCategory().getName(),
                record.getRecordDate(),
                record.getRecordTime(),
                record.getMemo(),
                record.getImageUrl()
        );
    }
}
