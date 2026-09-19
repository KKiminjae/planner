package com.planner.photo_calendar.record.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record DailyRecordUpdateRequest(
        @NotNull
        LocalTime recordTime,

        @NotBlank
        String memo
) {
}
