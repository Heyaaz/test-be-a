package com.example.be_a.enrollment.presentation;

import com.example.be_a.enrollment.application.ApplyEnrollmentCommand;
import jakarta.validation.constraints.NotNull;

public record ApplyEnrollmentRequest(
    @NotNull(message = "classId는 필수입니다.")
    Long classId,
    boolean waitlist
) {

    public ApplyEnrollmentCommand toCommand() {
        return new ApplyEnrollmentCommand(classId, waitlist);
    }
}
