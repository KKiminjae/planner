package com.planner.photo_calendar.record.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        String memo
) {
}
