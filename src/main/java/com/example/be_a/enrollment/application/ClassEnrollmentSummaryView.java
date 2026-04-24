package com.example.be_a.enrollment.application;

import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "크리에이터 수강생 목록 응답 항목")
public record ClassEnrollmentSummaryView(
    @Schema(description = "수강 신청 ID", example = "10")
    Long enrollmentId,
    @Schema(description = "수강생 사용자 ID", example = "2")
    Long userId,
    @Schema(description = "수강생 이름", example = "Student Two")
    String userName,
    @Schema(description = "수강 신청 상태", example = "CONFIRMED")
    EnrollmentStatus status,
    @Schema(description = "신청 시각", example = "2026-04-24T10:15:30")
    LocalDateTime requestedAt,
    @Schema(description = "결제 확정 시각", example = "2026-04-24T10:20:00")
    LocalDateTime confirmedAt,
    @Schema(description = "취소 시각", example = "2026-04-25T09:00:00")
    LocalDateTime cancelledAt
) {
}
