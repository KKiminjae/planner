package com.planner.photo_calendar.record.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalTime;

public record DailyRecordUpdateRequest(
        @NotNull
        LocalTime recordTime,

        String memo,

        @Pattern(regexp = "photos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)")
        String imageKey,
        Boolean removeImage
) {
    public DailyRecordUpdateRequest {
        memo = memo == null ? "" : memo;
    }

    public DailyRecordUpdateRequest(LocalTime recordTime, String memo) {
        this(recordTime, memo, null, null);
    }

    @AssertTrue(message = "사진 교체와 제거를 동시에 요청할 수 없습니다.")
    public boolean isImageChangeValid() {
        return !Boolean.TRUE.equals(removeImage) || imageKey == null;
    }
}
