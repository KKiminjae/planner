package com.planner.photo_calendar.common.exception;

public enum ErrorCode {
    AUTHENTICATION_REQUIRED(401, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(401, "로그인 정보를 확인해 주세요."),
    REQUEST_FORBIDDEN(403, "요청 권한 또는 CSRF 토큰을 확인해 주세요."),
    VALIDATION_FAILED(400, "요청값을 확인해 주세요."),
    INVALID_REQUEST(400, "올바르지 않은 요청입니다."),
    RECORD_BEFORE_CATEGORY_CREATION(400, "카테고리 생성일 이전에는 기록할 수 없습니다."),
    FUTURE_RECORD_NOT_ALLOWED(400, "미래 날짜에는 기록할 수 없습니다."),
    INVALID_CATEGORY_ORDER(400, "활성 카테고리 전체를 중복 없이 전달해야 합니다."),
    CATEGORY_NOT_FOUND(404, "존재하지 않는 카테고리입니다."),
    RECORD_NOT_FOUND(404, "존재하지 않는 기록입니다."),
    DUPLICATE_DAILY_RECORD(409, "해당 날짜에 이미 기록이 존재합니다."),
    PHOTO_NOT_FOUND(404, "업로드된 사진을 찾을 수 없습니다."),
    PHOTO_ALREADY_LINKED(409, "이미 다른 기록에 연결된 사진입니다."),
    INVALID_IMAGE(400, "JPEG 또는 PNG 이미지 파일을 업로드해 주세요."),
    IMAGE_TOO_LARGE(413, "사진은 5MB 이하, 2천만 화소 이하로 업로드해 주세요."),
    IMAGE_STORAGE_UNAVAILABLE(503, "사진 저장소에 연결할 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다.");
    private final int status;
    private final String message;
    ErrorCode(int status, String message) { this.status = status; this.message = message; }
    public int status() { return status; }
    public String message() { return message; }
}
