package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.domain.EnrollmentEntity;
import com.example.be_a.enrollment.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "수강 신청 응답")
public record EnrollmentResponse(
    @Schema(description = "수강 신청 ID", example = "10")
    Long id,
    @Schema(description = "강의 ID", example = "1")
    Long classId,
    @Schema(description = "수강생 사용자 ID", example = "2")
    Long userId,
    @Schema(description = "수강 신청 상태", example = "PENDING")
    EnrollmentStatus status,
    @Schema(description = "신청 시각", example = "2026-04-24T10:15:30")
    LocalDateTime requestedAt,
    @Schema(description = "결제 확정 시각. CONFIRMED 이후 노출", example = "2026-04-24T10:20:00")
    LocalDateTime confirmedAt,
    @Schema(description = "취소 시각. CANCELLED 이후 노출", example = "2026-04-25T09:00:00")
    LocalDateTime cancelledAt
) {

    public static EnrollmentResponse from(EnrollmentEntity enrollment) {
        return new EnrollmentResponse(
            enrollment.getId(),
            enrollment.getClassId(),
            enrollment.getUserId(),
            enrollment.getStatus(),
            enrollment.getRequestedAt(),
            enrollment.getConfirmedAt(),
            enrollment.getCancelledAt()
        );
    }
}
