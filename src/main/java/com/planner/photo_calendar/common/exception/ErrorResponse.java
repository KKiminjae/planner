package com.planner.photo_calendar.common.exception;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldErrorResponse> fieldErrors) {
    public static ErrorResponse from(ErrorCode code) {
        return new ErrorResponse(code.name(), code.message(), List.of());
    }
}
