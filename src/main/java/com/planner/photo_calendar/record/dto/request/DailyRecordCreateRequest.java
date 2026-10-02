package com.planner.photo_calendar.record.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

public record DailyRecordCreateRequest(
        @NotNull
        Long categoryId,

        @NotNull
        LocalDate recordDate,

        @NotNull
        LocalTime recordTime,

        @NotBlank
        String memo,

        @Pattern(regexp = "photos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)")
        String imageKey
) {
    public DailyRecordCreateRequest(Long categoryId, LocalDate recordDate, LocalTime recordTime, String memo) {
        this(categoryId, recordDate, recordTime, memo, null);
    }
}
