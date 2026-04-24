package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.application.ApplyEnrollmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "수강 신청 요청")
public record ApplyEnrollmentRequest(
    @Schema(description = "신청할 강의 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "classId는 필수입니다.")
    Long classId,

    @Schema(description = "정원 마감 시 대기열 등록 여부", example = "false")
    boolean waitlist
) {

    public ApplyEnrollmentCommand toCommand() {
        return new ApplyEnrollmentCommand(classId, waitlist);
    }
}
