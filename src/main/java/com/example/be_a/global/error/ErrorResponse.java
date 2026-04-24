package com.example.be_a.global.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
    @Schema(description = "에러 코드", example = "CLASS_FULL")
    String code,
    @Schema(description = "에러 메시지", example = "정원이 마감되었습니다.")
    String message,
    @Schema(description = "요청 경로", example = "/api/enrollments")
    String path,
    @Schema(description = "필드 검증 오류 목록")
    List<FieldErrorDetail> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), path, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), path, List.copyOf(fieldErrors));
    }
}
