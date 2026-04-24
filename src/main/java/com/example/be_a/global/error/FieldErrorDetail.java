package com.example.be_a.global.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "필드 검증 오류 상세")
public record FieldErrorDetail(
    @Schema(description = "오류 필드명", example = "capacity")
    String field,
    @Schema(description = "오류 사유", example = "정원은 1 이상이어야 합니다.")
    String reason
) {
}
