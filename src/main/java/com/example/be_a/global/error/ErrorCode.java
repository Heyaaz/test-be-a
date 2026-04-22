package com.example.be_a.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    MISSING_USER_ID(HttpStatus.BAD_REQUEST, "X-User-Id 헤더가 필요합니다."),
    INVALID_USER_ID(HttpStatus.BAD_REQUEST, "X-User-Id 헤더 형식이 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CREATOR_ONLY(HttpStatus.FORBIDDEN, "크리에이터만 접근할 수 있습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    CLASS_NOT_OPEN(HttpStatus.CONFLICT, "신청 가능한 강의가 아닙니다."),
    CLASS_NOT_DRAFT(HttpStatus.CONFLICT, "DRAFT 상태의 강의만 처리할 수 있습니다."),
    CLASS_UPDATE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 해당 강의를 수정할 수 없습니다."),
    CLASS_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 해당 강의를 삭제할 수 없습니다."),
    CLASS_FULL(HttpStatus.CONFLICT, "정원이 가득 찼습니다."),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "이미 수강 신청한 강의입니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "수강 신청 정보를 찾을 수 없습니다."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 상태 전이입니다."),
    CANCEL_PERIOD_EXPIRED(HttpStatus.CONFLICT, "취소 가능 기간이 지났습니다."),
    CONFLICT_RETRY(HttpStatus.CONFLICT, "동시성 충돌이 발생했습니다. 다시 시도해 주세요."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
