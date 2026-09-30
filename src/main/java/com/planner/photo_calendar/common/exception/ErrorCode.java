package com.planner.photo_calendar.common.exception;

public enum ErrorCode {
    VALIDATION_FAILED(400, "요청값을 확인해 주세요."),
    INVALID_REQUEST(400, "올바르지 않은 요청입니다."),
    RECORD_BEFORE_CATEGORY_CREATION(400, "카테고리 생성일 이전에는 기록할 수 없습니다."),
    FUTURE_RECORD_NOT_ALLOWED(400, "미래 날짜에는 기록할 수 없습니다."),
    INVALID_CATEGORY_ORDER(400, "활성 카테고리 전체를 중복 없이 전달해야 합니다."),
    CATEGORY_NOT_FOUND(404, "존재하지 않는 카테고리입니다."),
    RECORD_NOT_FOUND(404, "존재하지 않는 기록입니다."),
    DUPLICATE_DAILY_RECORD(409, "해당 날짜에 이미 기록이 존재합니다."),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다.");
    private final int status;
    private final String message;
    ErrorCode(int status, String message) { this.status = status; this.message = message; }
    public int status() { return status; }
    public String message() { return message; }
}
